package com.co.kc.imchat.broker.interfaces.listener.gateway;

import com.co.kc.imchat.broker.interfaces.listener.AbstractRegistryEventListener;
import com.co.kc.imchat.broker.domain.registry.gateway.GatewayRegistry;
import com.co.kc.imchat.broker.support.event.model.GatewayHeartbeatEvent;
import com.co.kc.imchat.broker.support.event.model.GatewayRegisteredEvent;
import com.co.kc.imchat.broker.support.event.model.GatewayRemovedEvent;
import com.co.kc.imchat.broker.domain.store.BrokerStateStore;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Gateway 注册表事件监听器。
 * <p>
 * 将 gateway 注册、心跳、移除事件同步到 broker gossip 状态。
 */
@Component
public class GatewayRegistryEventListener extends AbstractRegistryEventListener {
    private final GatewayRegistry gatewayRegistry;
    private final BrokerStateStore brokerStateStore;

    public GatewayRegistryEventListener(GatewayRegistry gatewayRegistry,
                                        BrokerStateStore brokerStateStore) {
        this.gatewayRegistry = gatewayRegistry;
        this.brokerStateStore = brokerStateStore;
    }

    @EventListener
    public void onGatewayRegistered(GatewayRegisteredEvent event) {
        handle(() -> brokerStateStore.putGatewayState(event.gatewayId(), event.host(), event.port()),
                "gateway registered");
    }

    @EventListener
    public void onGatewayHeartbeat(GatewayHeartbeatEvent event) {
        handle(() -> gatewayRegistry.find(event.gatewayId())
                .ifPresent(endpoint -> brokerStateStore.putGatewayState(
                        endpoint.gatewayId(), endpoint.host(), endpoint.port())), "gateway heartbeat");
    }

    @EventListener
    public void onGatewayRemoved(GatewayRemovedEvent event) {
        handle(() -> brokerStateStore.removeGatewayState(event.gatewayId()), "gateway removed");
    }
}
