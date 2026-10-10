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
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
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
import com.co.kc.imchat.broker.sdk.model.result.BrokerListResult;
import com.co.kc.imchat.broker.sdk.model.result.AcceptGatewayRoutesResult;
import com.co.kc.imchat.broker.sdk.model.result.GatewayRouteSnapshotAckResult;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Broker 客户端。
 * <p>
 * WS 网关通过该客户端注册自身、同步连接状态，并把客户端上行帧转交 Broker。
 * 当前内部使用 Bolt 调用 Broker，不向上层暴露具体通信协议。
 */
@Slf4j
public class BrokerClient {
    private final BoltInvoker boltInvoker;
    private final AtomicReference<List<String>> brokerAddresses;
    private final BrokerAddressSelector brokerAddressSelector;
    private final BrokerClusterStateCache clusterStateCache = new BrokerClusterStateCache();
    private final int timeoutMillis;

    /**
     * 从服务发现获取初始 Broker 地址列表创建客户端。
     *
     * <p>初始地址仅用于第一次调用，后续地址通过 Broker 集群快照刷新。</p>
     *
     * @param boltInvoker    Bolt 调用器
     * @param discoveryClient 服务发现客户端
     * @param serviceName    Broker 服务发现名称
     * @param loadBalance    地址负载均衡策略
     * @param timeoutMillis  调用超时时间
     */
    public BrokerClient(BoltInvoker boltInvoker,
                        DiscoveryClient discoveryClient,
                        ServiceName serviceName,
                        BrokerLoadBalance loadBalance,
                        int timeoutMillis) {
        AssertUtils.argNotNull("discovery client must not be null", discoveryClient);
        AssertUtils.argNotNull("broker service name must not be null", serviceName);
        this.boltInvoker = boltInvoker;
        this.brokerAddresses = new AtomicReference<>(discoverBootstrapAddresses(discoveryClient, serviceName));
        this.brokerAddressSelector = createBrokerAddressSelector(loadBalance);
        this.timeoutMillis = timeoutMillis;
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
     * 查询 Broker 实例快照。
     *
     * @return Broker 实例列表
     */
    @SuppressWarnings("unchecked")
    public List<BrokerEndpointDTO> listBrokers() {
        BrokerListResult result = invokeBroker(BrokerBoltOperation.LIST_BROKERS,
                new BrokerQueryParams(), null, BrokerListResult.class);
        return result.brokers();
    }

    /**
     * 从 bootstrap Broker 拉取并原子安装 Raft committed cluster state。
     */
    public void refreshClusterState() {
        BrokerClusterState state = invokeBroker(BrokerBoltOperation.GET_CLUSTER_STATE,
                new BrokerQueryParams(), null, BrokerClusterState.class);
        clusterStateCache.install(state);
        brokerAddresses.set(state.activeBrokers().stream()
                .map(BrokerNode::routeEndpoint)
                .distinct()
                .toList());
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
        String endpoint = state.activeBrokers().stream()
                .filter(broker -> broker.brokerId().equals(params.routeBrokerId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "RouteBroker is not ACTIVE: " + params.routeBrokerId()))
                .routeEndpoint();
        return invokeBroker(endpoint, BrokerBoltOperation.APPLY_GATEWAY_ROUTE_SNAPSHOT,
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
                        broker.routeEndpoint(),
                        BrokerBoltOperation.ACK_GATEWAY_ROUTE_SNAPSHOT,
                        params, GatewayRouteSnapshotAckResult.class);
                if (result.status() != GatewayRouteSnapshotAckResult.Status.NOT_LEADER) {
                    return result;
                }
            } catch (RuntimeException exception) {
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
        return invokeBroker(nextBrokerAddress(routeKey), operation, params, responseType);
    }

    private <T, R> R invokeBroker(String address, BrokerBoltOperation operation,
                                  T params, Class<R> responseType) {
        return boltInvoker.invoke(address, operation.service().service(),
                operation.operation(), params, responseType, timeoutMillis);
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
        RouteRequestContext context = new RouteRequestContext(state.epoch(), operationId,
                System.currentTimeMillis() + timeoutMillis);
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
        if (System.currentTimeMillis() >= context.deadlineEpochMillis()) {
            throw new IllegalStateException("RouteBroker retry deadline exceeded for operation "
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
        return invokeBroker(routeBrokerEndpoint(params.userId(), state), operation,
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

    private String nextBrokerAddress(String routeKey) {
        return brokerAddressSelector.select(brokerAddresses.get(), routeKey);
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
