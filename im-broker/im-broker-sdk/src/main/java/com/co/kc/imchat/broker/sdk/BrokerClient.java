package com.co.kc.imchat.broker.sdk;

import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
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
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.model.result.BrokerListResult;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;

import java.util.Arrays;
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

    protected BrokerClient() {
        this(null, "127.0.0.1:0", BrokerLoadBalance.HASH, 0);
    }

    public BrokerClient(BoltInvoker boltInvoker, String brokerAddress, int timeoutMillis) {
        this(boltInvoker, brokerAddress, BrokerLoadBalance.HASH, timeoutMillis);
    }

    public BrokerClient(BoltInvoker boltInvoker, String brokerAddress,
                        BrokerLoadBalance loadBalance, int timeoutMillis) {
        this.boltInvoker = boltInvoker;
        this.brokerAddresses = new AtomicReference<>(parseBrokerAddresses(brokerAddress));
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

    private List<String> parseBrokerAddresses(String brokerAddress) {
        if (brokerAddress == null || brokerAddress.isBlank()) {
            throw new IllegalArgumentException("broker address must not be blank");
        }
        List<String> addresses = Arrays.stream(brokerAddress.split(","))
                .map(String::trim)
                .filter(address -> !address.isBlank())
                .toList();
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("broker address must not be blank");
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
