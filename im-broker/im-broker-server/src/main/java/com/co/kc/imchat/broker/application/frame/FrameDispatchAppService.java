package com.co.kc.imchat.broker.application.frame;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.gateway.GatewayDispatchAdapter;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.model.cqrs.command.FrameDispatchCommand;
import com.co.kc.imchat.broker.model.cqrs.result.RouteLocation;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerFrameWriteDTO;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.alicp.jetcache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;

import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 编排当前 RouteBroker 的下行帧定位与 Gateway 定向投递。
 * 投递入口直接展开本地定位、单轮有界并发查询、远端位置复核和逐 Gateway 写入。
 * 查询目标来自当前已提交拓扑；负缓存不替代实际路由。
 */
@Slf4j
@RequiredArgsConstructor
public final class FrameDispatchAppService {
    private final ConnectionRegistry connectionRegistry;
    private final GatewayRegistry gatewayRegistry;
    private final BrokerRaftAdapter brokerClusterAdapter;
    private final BrokerPeerAdapter brokerPeerAdapter;
    private final GatewayDispatchAdapter gatewayDispatchAdapter;
    private final Duration gatewayTtl;
    private final Duration negativeCacheDuration;
    /* 未命中用户的短期抑制标记，TTL 与容量由本地 JetCache 管理。 */
    private final Cache<UserId, Boolean> negativeCache;

    /**
     * 定位用户 Gateway 后下行投递，所有定位步骤在本方法中按执行顺序展开。
     * <p>核心步骤：转换和校验输入 → 本地定位 → 检查查询窗口 → 单轮并发查询
     * → 维护负缓存 → 复核远端 Gateway → 逐目标投递并汇总。</p>
     * 本地命中不查 peer；其他可路由 Broker 并发查询，各自受查询超时限制，单个节点失败不阻断其他目标。
     *
     * @return 连接写入结果；无有效目标返回路由不可用
     * @throws IllegalArgumentException 帧内容缺失或 deliveryId 为空白，交协议边界处理
     * @throws IllegalStateException    用户领域身份缺失，交协议边界处理
     */
    public BrokerFrameWriteResult dispatch(FrameDispatchCommand command) {
        // 1. 转换用户身份并逐项校验输入，参数异常交外部统一处理。
        UserId userId = new UserId(command.userId());
        RoutingEpoch routingEpoch = new RoutingEpoch(command.routingEpoch());

        AssertUtils.argNotNull("outbound frame is required", command.frame());
        AssertUtils.argNotBlank("deliveryId is required", command.deliveryId());

        BrokerId localBrokerId = brokerClusterAdapter.getLocalBroker().getId();

        // 2. 本地活跃 Gateway 优先，按身份去重；本地命中直接进入最后的投递步骤。
        Map<GatewayId, GatewayRegistration> userGateways = new LinkedHashMap<>();
        for (ConnectionRegistration registration : connectionRegistry.find(userId)) {
            gatewayRegistry.find(registration.gatewayId())
                    .filter(gateway -> gateway.isFreshAt(Instant.now(), gatewayTtl))
                    .ifPresent(gateway -> userGateways.putIfAbsent(gateway.gatewayId(), gateway));
        }

        if (MapUtils.isEmpty(userGateways)) {
            // 3. 请求期限已由入口校验；近期未命中时不重复查询 peer。
            if (Boolean.TRUE.equals(negativeCache.get(userId))) {
                return BrokerFrameWriteResult.failed("ROUTE_UNAVAILABLE", "目标用户连接路由暂不可用");
            }

            CompletableFuture.completedFuture(brokerClusterAdapter.getCommittedState())
                    // 4. 从当前拓扑选择所有其他可路由 Broker。
                    .thenApply(state ->
                            state.brokers().stream()
                                    .filter(broker -> !localBrokerId.value().equals(broker.brokerId()))
                                    .filter(broker -> broker.routingState() == BrokerRoutingState.ACTIVE || broker.routingState() == BrokerRoutingState.DRAINING)
                                    .toList())
                    // 5. 并发请求所有候选，汇总完成的响应；单个失败按空结果处理。
                    .thenCompose(candidates ->
                            candidates.stream()
                                    .map(broker -> brokerPeerAdapter.lookupRoutes(broker, userId, routingEpoch)
                                            .exceptionally(failure -> List.of()))
                                    .reduce(CompletableFuture.completedFuture(List.of()),
                                            (collected, response) -> collected.thenCombine(response, ListUtils::union)))
                    // 6. 排除未来 epoch 的描述，同一 Gateway 保留最高 revision。
                    .thenApply(routes ->
                            routes.stream()
                                    .filter(route -> route.observedEpoch() <= routingEpoch.value())
                                    .collect(Collectors.toMap(RouteLocation::gatewayId, Function.identity(),
                                            BinaryOperator.maxBy(Comparator.comparingLong(RouteLocation::routeRevision)), LinkedHashMap::new)))
                    // 7. 路由描述只是候选，投递前复核注册端点和 Gateway 活跃观察。
                    .thenAccept(gatewayRoutes -> {
                        Instant checkedAt = Instant.now();
                        for (RouteLocation routeLocation : gatewayRoutes.values()) {
                            gatewayRegistry.find(new GatewayId(routeLocation.gatewayId()))
                                    .filter(gateway -> gateway.isFreshAt(checkedAt, gatewayTtl))
                                    .filter(gateway -> gateway.host().value().equals(routeLocation.gatewayHost()) && gateway.port().value() == routeLocation.gatewayPort())
                                    .ifPresent(gateway -> userGateways.putIfAbsent(gateway.gatewayId(), gateway));
                        }
                    })
                    .join();

            // 8. 从最终可投递目标判断未命中，负缓存窗口从复核完成时起算。
            if (userGateways.isEmpty() && !negativeCacheDuration.isZero()) {
                negativeCache.put(userId, true);
            } else {
                negativeCache.remove(userId);
            }
        }

        // 9. 没有有效目标时明确返回不可用，不将空定位解释为已投递。
        if (MapUtils.isEmpty(userGateways)) {
            return BrokerFrameWriteResult.failed("ROUTE_UNAVAILABLE", "目标用户连接路由暂不可用");
        }

        return CompletableFuture.completedFuture(userGateways.values())
                // 10. 在专用虚拟线程中并发写入各 Gateway；失败目标不影响其他结果。
                .thenCompose(gateways -> gateways.stream()
                        .map(gateway -> gatewayDispatchAdapter.dispatch(gateway, command)
                                .exceptionally(failure -> {
                                    log.warn("failed to write outbound frame to gateway:{}, error:{}", gateway.gatewayId(), failure.toString());
                                    return List.of();
                                }))
                        .reduce(CompletableFuture.completedFuture(List.of()),
                                (collected, response) -> collected.thenCombine(response, ListUtils::union)))
                // 11. 全部目标结束后汇总连接写入结果。
                .thenApply(results -> BrokerFrameWriteResult.writeResult(userId.value(), results))
                .join();
    }

}
