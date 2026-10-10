package com.co.kc.imchat.broker.application.local;

import com.co.kc.imchat.broker.adapter.gossip.BrokerStateStore;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 清理心跳超期 Gateway 的本机连接路由，先保存可传播的 Gossip 删除事实。 */
public final class LocalGatewayCleanupAppService {
    private final GatewayRegistry gatewayRegistry;
    private final ConnectionRegistry connectionRegistry;
    private final BrokerStateStore stateStore;
    private final Duration gatewayTtl;
    private final Duration gracePeriod;

    public LocalGatewayCleanupAppService(GatewayRegistry gatewayRegistry,
                                         ConnectionRegistry connectionRegistry,
                                         BrokerStateStore stateStore,
                                         Duration gatewayTtl, Duration gracePeriod) {
        this.gatewayRegistry = gatewayRegistry;
        this.connectionRegistry = connectionRegistry;
        this.stateStore = stateStore;
        this.gatewayTtl = gatewayTtl;
        this.gracePeriod = gracePeriod;
    }

    /** Gossip 删除写入失败时不移除注册，使下轮扫描仍能重试。 */
    public void cleanupExpired(Instant now) {
        for (GatewayRegistration gateway : gatewayRegistry.list()) {
            if (gateway.lastSeenAt().plus(gatewayTtl).plus(gracePeriod).isAfter(now)) {
                continue;
            }
            stateStore.removeGatewayState(gateway.gatewayId().value());
            connectionRegistry.removeGateway(gateway.gatewayId());
            gatewayRegistry.unregister(gateway.gatewayId());
        }
        connectionRegistry.collectExpiredTombstones(gracePeriod);
    }

    /** 显式注销先保存可传播的删除事实；写入失败时保留注册与连接供重试。 */
    public List<ConnectionRegistration> unregister(GatewayId gatewayId) {
        stateStore.removeGatewayState(gatewayId.value());
        List<ConnectionRegistration> removed = connectionRegistry.removeGateway(gatewayId).removed();
        gatewayRegistry.unregister(gatewayId);
        return removed;
    }
}
