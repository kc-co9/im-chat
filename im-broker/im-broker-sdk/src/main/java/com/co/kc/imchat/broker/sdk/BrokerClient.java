package com.co.kc.imchat.broker.sdk;

import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterStateCache;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerStatus;
import com.co.kc.imchat.broker.sdk.routing.RoutableBrokerRequest;
import com.co.kc.imchat.broker.sdk.routing.RouteRequestContext;
import com.co.kc.imchat.broker.sdk.routing.RouteBrokerSelectors;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.broker.sdk.loadbalance.BrokerAddressSelector;
import com.co.kc.imchat.broker.sdk.loadbalance.HashBrokerAddressSelector;
import com.co.kc.imchat.broker.sdk.loadbalance.RandomBrokerAddressSelector;
import com.co.kc.imchat.broker.sdk.loadbalance.RoundRobinBrokerAddressSelector;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerQueryParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotAckParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.model.result.AcceptGatewayRoutesResult;
import com.co.kc.imchat.broker.sdk.model.result.GatewayRouteSnapshotAckResult;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.common.constant.PeerIdentityConstants;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Broker 客户端。
 * <p>
 * WS 网关通过该客户端注册自身、同步连接状态，并把客户端上行帧转交 Broker。
 * 当前内部使用 Bolt 调用 Broker，不向上层暴露具体通信协议。
 */
@Slf4j
public class BrokerClient {
    private final BoltInvoker boltInvoker;
    private final List<BoltTarget> bootstrapTargets;
    private final BrokerAddressSelector brokerAddressSelector;
    private final BrokerClusterStateCache clusterStateCache = new BrokerClusterStateCache();
    private final Duration timeout;

    /**
     * 将服务发现候选与部署可信身份配对求交后创建客户端。
     *
     * <p>首次调用不从metadata或响应学习身份；安装committed state后从完整节点选择目标。</p>
     *
     * @param boltInvoker    Bolt 调用器
     * @param discoveryClient 服务发现客户端
     * @param serviceName    Broker 服务发现名称
     * @param loadBalance    地址负载均衡策略
     * @param timeoutMs  调用超时时间
     * @param bootstrapTargets 固定项目命名空间下的部署 bootstrap 可信配对
     */
    public BrokerClient(BoltInvoker boltInvoker,
                        DiscoveryClient discoveryClient,
                        ServiceName serviceName,
                        BrokerLoadBalance loadBalance,
                        int timeoutMs,
                        List<BoltTarget> bootstrapTargets) {
        AssertUtils.argNotNull("discovery client must not be null", discoveryClient);
        AssertUtils.argNotNull("broker service name must not be null", serviceName);
        this.boltInvoker = boltInvoker;
        List<BoltTarget> trustedTargets = validateBootstrapTargets(bootstrapTargets);
        this.bootstrapTargets = selectBootstrapTargets(trustedTargets, discoverBootstrapAddresses(discoveryClient, serviceName));
        this.brokerAddressSelector = createBrokerAddressSelector(loadBalance);
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    /**
     * 向 Broker 注册当前 WS 网关实例。
     *
     * @param params 网关注册参数
     */
    public void registerGateway(GatewayRegisterParams params) {
        invokeBroker(BrokerBoltOperation.REGISTER_GATEWAY, params, params.gatewayId(), Void.class);
    }

    /**
     * 注册 Broker 实例。
     *
     * @param params Broker 注册参数
     */
    public void registerBroker(BrokerRegisterParams params) {
        invokeBroker(BrokerBoltOperation.REGISTER_BROKER, params, params.brokerId(), Void.class);
    }

    /**
     * 注销 Broker 实例。
     *
     * @param params Broker 注销参数
     */
    public void unregisterBroker(BrokerUnregisterParams params) {
        invokeBroker(BrokerBoltOperation.UNREGISTER_BROKER, params, params.brokerId(), Void.class);
    }

    /**
     * 上报 Broker 实例心跳。
     *
     * @param params Broker 心跳参数
     */
    public void heartbeatBroker(BrokerHeartbeatParams params) {
        invokeBroker(BrokerBoltOperation.HEARTBEAT_BROKER, params, params.brokerId(), Void.class);
    }

    /**
     * 从 bootstrap Broker 拉取并原子安装 Raft committed cluster state。
     */
    public void refreshClusterState() {
        BrokerClusterState state = invokeBroker(BrokerBoltOperation.GET_CLUSTER_STATE,
                new BrokerQueryParams(), null, BrokerClusterState.class);
        clusterStateCache.install(state);
    }

    /** 返回当前已安装的权威集群状态，首次访问时完成 bootstrap。 */
    public BrokerClusterState currentClusterState() {
        return requireClusterState();
    }

    /**
     * 向 Broker 注销当前 WS 网关实例。
     *
     * @param params 网关注销参数
     */
    public void unregisterGateway(GatewayUnregisterParams params) {
        invokeBroker(BrokerBoltOperation.UNREGISTER_GATEWAY, params, params.gatewayId(), Void.class);
    }

    /**
     * 向 Broker 上报当前 WS 网关实例心跳。
     *
     * @param params 网关心跳参数
     */
    public void heartbeatGateway(GatewayHeartbeatParams params) {
        invokeBroker(BrokerBoltOperation.HEARTBEAT_GATEWAY, params, params.gatewayId(), Void.class);
    }

    /**
     * 向 Broker 同步当前网关持有的连接快照。
     *
     * @param params 连接同步参数
     */
    public AcceptGatewayRoutesResult applyGatewayRouteSnapshot(
            GatewayRouteSnapshotParams params) {
        BrokerClusterState state = requireClusterState();
        BrokerNode target = state.activeBrokers().stream()
                .filter(broker -> broker.brokerId().equals(params.routeBrokerId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "RouteBroker is not ACTIVE: " + params.routeBrokerId()));
        return invokeBroker(target(target), BrokerBoltOperation.APPLY_GATEWAY_ROUTE_SNAPSHOT,
                params, AcceptGatewayRoutesResult.class);
    }

    /**
     * 向当前 Raft Leader 确认 Gateway 已完成目标 epoch 的整轮分片快照。
     */
    public GatewayRouteSnapshotAckResult acknowledgeGatewayRouteSnapshot(
            GatewayRouteSnapshotAckParams params) {
        BrokerClusterState state = requireClusterState();
        RuntimeException lastFailure = null;
        for (BrokerNode broker : state.brokers()) {
            try {
                GatewayRouteSnapshotAckResult result = invokeBroker(
                        target(broker),
                        BrokerBoltOperation.ACK_GATEWAY_ROUTE_SNAPSHOT,
                        params, GatewayRouteSnapshotAckResult.class);
                if (result.status() != GatewayRouteSnapshotAckResult.Status.NOT_LEADER) {
                    return result;
                }
            } catch (RuntimeException exception) {
                if (exception instanceof BoltInvocationException invocation
                        && invocation.failure() == BoltInvocationException.Failure.AUTHENTICATION) { throw exception; }
                lastFailure = exception;
            }
        }
        if (lastFailure != null) {
            throw new IllegalStateException(
                    "Gateway route snapshot ACK could not reach the Raft Leader", lastFailure);
        }
        return new GatewayRouteSnapshotAckResult(
                GatewayRouteSnapshotAckResult.Status.NOT_LEADER,
                "Raft Leader is unavailable");
    }

    /**
     * 向 Broker 注册单个用户连接。
     *
     * @param params 连接注册参数
     */
    public void registerConnection(ConnectionRegisterParams params) {
        invokeUserRoute(BrokerBoltOperation.REGISTER_CONNECTION, params, Void.class);
    }

    /**
     * 向 Broker 注销单个用户连接。
     *
     * @param params 连接注销参数
     */
    public void unregisterConnection(ConnectionUnregisterParams params) {
        invokeUserRoute(BrokerBoltOperation.UNREGISTER_CONNECTION, params, Void.class);
    }

    /** 请求 Broker 关闭用户连接。 */
    public void closeConnections(ConnectionCloseParams params) {
        closeConnections(null, params);
    }

    /** 使用稳定 operationId 请求 Broker 关闭用户连接。 */
    public void closeConnections(String operationId, ConnectionCloseParams params) {
        invokeUserRoute(BrokerBoltOperation.CLOSE_CONNECTIONS, params, Void.class, operationId);
    }

    /**
     * 把实时帧交给 Broker 统一处理。
     *
     * @param params Broker 实时帧处理参数
     * @return Broker 处理结果
     */
    public BrokerFrameWriteResult writeFrame(BrokerFrameWriteParams params) {
        return writeFrame(null, params);
    }

    /** 使用稳定业务标识把实时帧交给 Broker。 */
    public BrokerFrameWriteResult writeFrame(String operationId, BrokerFrameWriteParams params) {
        return invokeUserRoute(BrokerBoltOperation.WRITE_FRAME,
                params, BrokerFrameWriteResult.class, operationId);
    }

    private <T, R> R invokeBroker(BrokerBoltOperation operation, T params,
                                  String routeKey, Class<R> responseType) {
        return invokeBroker(nextBrokerTarget(routeKey), operation, params, responseType);
    }

    private <T, R> R invokeBroker(BoltTarget target, BrokerBoltOperation operation,
                                  T params, Class<R> responseType) {
        return boltInvoker.invoke(target, operation.service().service(),
                operation.operation(), params, responseType, Math.toIntExact(timeout.toMillis()));
    }

    private <T extends RoutableBrokerRequest<T>, R> R invokeUserRoute(
            BrokerBoltOperation operation,
            T params,
            Class<R> responseType) {
        return invokeUserRoute(operation, params, responseType, null);
    }

    private <T extends RoutableBrokerRequest<T>, R> R invokeUserRoute(
            BrokerBoltOperation operation,
            T params,
            Class<R> responseType,
            String requestedOperationId) {
        Long userId = params.userId();
        if (userId == null) {
            throw new IllegalArgumentException("userId is required for RouteBroker selection");
        }
        BrokerClusterState state = requireClusterState();
        String operationId = requestedOperationId == null || requestedOperationId.isBlank()
                ? UUID.randomUUID().toString() : requestedOperationId;
        Instant expireAt = Instant.ofEpochMilli(System.currentTimeMillis()).plus(timeout);
        RouteRequestContext context = new RouteRequestContext(state.epoch(), operationId,
                expireAt.toEpochMilli());
        RouteBrokerResult first = invokeRouteAttempt(state, operation,
                params.withRouteContext(context));
        if (first.status() == RouteBrokerStatus.EXECUTED) {
            return routeValue(first, responseType);
        }

        refreshClusterState();
        BrokerClusterState refreshed = requireClusterState();
        String refreshedEndpoint = routeBrokerEndpoint(userId, refreshed);
        if (first.status() == RouteBrokerStatus.ROUTE_MISMATCH
                && first.routingEpoch() == refreshed.epoch()
                && first.suggestedEndpoint() != null
                && !first.suggestedEndpoint().equals(refreshedEndpoint)) {
            throw new IllegalStateException("RouteBroker selector mismatch at epoch "
                    + refreshed.epoch() + ": local=" + refreshedEndpoint
                    + ", remote=" + first.suggestedEndpoint());
        }
        if (!Instant.ofEpochMilli(System.currentTimeMillis()).isBefore(expireAt)) {
            throw new IllegalStateException("RouteBroker retry expiry exceeded for operation "
                    + context.operationId());
        }

        RouteBrokerResult second = invokeRouteAttempt(refreshed, operation,
                params.withRouteContext(context.withRoutingEpoch(refreshed.epoch())));
        if (second.status() != RouteBrokerStatus.EXECUTED) {
            log.error("RouteBroker rejected directed retry, operationId:{}, status:{}, localEpoch:{}, endpoint:{}",
                    context.operationId(), second.status(), second.routingEpoch(),
                    second.suggestedEndpoint());
            throw new IllegalStateException("RouteBroker rejected directed retry: "
                    + second.status() + ", localEpoch=" + second.routingEpoch());
        }
        return routeValue(second, responseType);
    }

    private <T extends RoutableBrokerRequest<T>> RouteBrokerResult invokeRouteAttempt(
            BrokerClusterState state,
            BrokerBoltOperation operation,
            T params) {
        return invokeBroker(target(RouteBrokerSelectors.forAlgorithm(state.algorithm())
                        .select(params.userId(), state)), operation,
                params, RouteBrokerResult.class);
    }

    private String routeBrokerEndpoint(Long userId, BrokerClusterState state) {
        return RouteBrokerSelectors.forAlgorithm(state.algorithm())
                .select(userId, state).routeEndpoint();
    }

    private <R> R routeValue(RouteBrokerResult result, Class<R> responseType) {
        if (responseType == Void.class) {
            return null;
        }
        return JsonUtils.convertValue(result.value(), responseType);
    }

    private BrokerClusterState requireClusterState() {
        java.util.Optional<BrokerClusterState> state = clusterStateCache.current();
        if (state.isEmpty()) {
            refreshClusterState();
            state = clusterStateCache.current();
        }
        return state.orElseThrow(() -> new IllegalStateException(
                "BrokerClusterState is unavailable after bootstrap"));
    }

    private BoltTarget nextBrokerTarget(String routeKey) {
        List<BoltTarget> targets = clusterStateCache.current()
                .map(state -> state.activeBrokers().stream().map(this::target).toList())
                .orElse(bootstrapTargets);
        String address = brokerAddressSelector.select(targets.stream().map(BoltTarget::address).toList(), routeKey);
        List<BoltTarget> selected = targets.stream().filter(target -> target.address().equals(address)).distinct().toList();
        if (selected.size() != 1) { throw new IllegalStateException("Conflicting committed Broker endpoint identities"); }
        return selected.getFirst();
    }

    /** 校验并复制部署可信配对，发现查询前拒绝错误命名空间与冲突绑定。 */
    private List<BoltTarget> validateBootstrapTargets(List<BoltTarget> bootstrapTargets) {
        if (bootstrapTargets == null) {
            throw new IllegalArgumentException("Broker bootstrap identity pairs are required");
        }
        bootstrapTargets = List.copyOf(bootstrapTargets);
        if (bootstrapTargets.isEmpty()) {
            throw new IllegalArgumentException("Broker bootstrap identity pairs are required");
        }
        Map<String, String> identities = new LinkedHashMap<>();
        Map<String, String> addresses = new LinkedHashMap<>();
        for (BoltTarget target : bootstrapTargets) {
            if (!target.expectedPeerIdentity().startsWith(PeerIdentityConstants.BROKER_URI_PREFIX)
                    || target.expectedPeerIdentity().length() == PeerIdentityConstants.BROKER_URI_PREFIX.length()) {
                throw new IllegalArgumentException("Broker bootstrap identity does not match fixed prefix");
            }
            String previousIdentity = identities.putIfAbsent(target.address(), target.expectedPeerIdentity());
            String previousAddress = addresses.putIfAbsent(target.expectedPeerIdentity(), target.address());
            if ((previousIdentity != null && !previousIdentity.equals(target.expectedPeerIdentity()))
                    || (previousAddress != null && !previousAddress.equals(target.address()))) {
                throw new IllegalArgumentException("Conflicting Broker bootstrap identity pair");
            }
        }
        return bootstrapTargets;
    }

    /** 仅部署可信配对与发现地址的交集可用于首次连接，不从发现结果学习身份。 */
    private List<BoltTarget> selectBootstrapTargets(List<BoltTarget> bootstrapTargets, List<String> discoveredAddresses) {
        List<BoltTarget> selected = bootstrapTargets.stream()
                .filter(target -> discoveredAddresses.contains(target.address())).distinct().toList();
        if (selected.isEmpty()) {
            throw new IllegalStateException("No discovered Broker has a trusted bootstrap identity pair");
        }
        return selected;
    }

    /* 使用 committed 节点的实际地址和固定项目身份构造后续连接目标。 */
    private BoltTarget target(BrokerNode node) {
        return new BoltTarget(node.routeEndpoint(), PeerIdentityConstants.BROKER_URI_PREFIX + node.brokerId());
    }

    private List<String> discoverBootstrapAddresses(
            DiscoveryClient discoveryClient,
            ServiceName serviceName) {
        List<String> addresses = discoveryClient.getInstances(serviceName.value()).stream()
                .map(instance -> instance.getHost() + ":" + instance.getPort())
                .distinct()
                .toList();
        if (addresses.isEmpty()) {
            throw new IllegalStateException("No Broker instances found for service: " + serviceName.value());
        }
        return addresses;
    }

    private BrokerAddressSelector createBrokerAddressSelector(BrokerLoadBalance loadBalance) {
        BrokerLoadBalance resolvedLoadBalance = loadBalance == null ? BrokerLoadBalance.HASH : loadBalance;
        return switch (resolvedLoadBalance) {
            case ROUND_ROBIN -> new RoundRobinBrokerAddressSelector();
            case RANDOM -> new RandomBrokerAddressSelector();
            case HASH -> new HashBrokerAddressSelector();
        };
    }
}
