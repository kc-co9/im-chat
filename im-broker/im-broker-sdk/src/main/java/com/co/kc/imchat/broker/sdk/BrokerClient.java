package com.co.kc.imchat.broker.sdk;

import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
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
import com.co.kc.imchat.broker.sdk.model.params.ConnectionSyncParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.model.result.BrokerListResult;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Broker 客户端。
 * <p>
 * WS 网关通过该客户端注册自身、同步连接状态，并把客户端上行帧转交 Broker。
 * 当前内部使用 Bolt 调用 Broker，不向上层暴露具体通信协议。
 */
public class BrokerClient {
    private final BoltInvoker boltInvoker;
    private final AtomicReference<List<String>> brokerAddresses;
    private final BrokerAddressSelector brokerAddressSelector;
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
     * 从 Broker 侧同步最新 Broker 地址快照。
     */
    public void refreshBrokerAddresses() {
        if (boltInvoker == null) {
            return;
        }
        List<String> addresses = listBrokers().stream()
                .sorted(Comparator.comparing(BrokerEndpointDTO::brokerId))
                .map(BrokerEndpointDTO::address)
                .distinct()
                .toList();
        if (!addresses.isEmpty()) {
            brokerAddresses.set(addresses);
        }
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
    public void syncConnections(ConnectionSyncParams params) {
        RuntimeException firstFailure = null;
        boolean hasSuccess = false;
        for (String brokerAddress : brokerAddresses.get()) {
            try {
                invokeBroker(brokerAddress, BrokerBoltOperation.SYNC_CONNECTIONS, params, Void.class);
                hasSuccess = true;
            } catch (RuntimeException ex) {
                if (firstFailure == null) {
                    firstFailure = ex;
                }
            }
        }
        if (!hasSuccess && firstFailure != null) {
            throw firstFailure;
        }
    }

    /**
     * 向 Broker 注册单个用户连接。
     *
     * @param params 连接注册参数
     */
    public void registerConnection(ConnectionRegisterParams params) {
        invokeBroker(BrokerBoltOperation.REGISTER_CONNECTION, params, connectionRouteKey(params.userId(), params.gatewayId()),
                Void.class);
    }

    /**
     * 向 Broker 注销单个用户连接。
     *
     * @param params 连接注销参数
     */
    public void unregisterConnection(ConnectionUnregisterParams params) {
        invokeBroker(BrokerBoltOperation.UNREGISTER_CONNECTION, params,
                connectionRouteKey(params.userId(), params.gatewayId()), Void.class);
    }

    /** 请求 Broker 关闭用户连接。 */
    public void closeConnections(ConnectionCloseParams params) {
        invokeBroker(BrokerBoltOperation.CLOSE_CONNECTIONS, params,
                String.valueOf(params.userId()), Void.class);
    }

    /**
     * 把实时帧交给 Broker 统一处理。
     *
     * @param params Broker 实时帧处理参数
     * @return Broker 处理结果
     */
    public BrokerFrameWriteResult writeFrame(BrokerFrameWriteParams params) {
        return invokeBroker(BrokerBoltOperation.WRITE_FRAME, params, frameRouteKey(params), BrokerFrameWriteResult.class);
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

    private String nextBrokerAddress(String routeKey) {
        return brokerAddressSelector.select(brokerAddresses.get(), routeKey);
    }

    private String frameRouteKey(BrokerFrameWriteParams params) {
        if (params.userId() != null) {
            return String.valueOf(params.userId());
        }
        return params.connectionId();
    }

    private String connectionRouteKey(Long userId, String fallback) {
        if (userId != null) {
            return String.valueOf(userId);
        }
        return fallback;
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
