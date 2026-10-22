package com.co.kc.imchat.broker.interfaces.listener.gateway;

import com.co.kc.imchat.broker.application.gossip.BrokerGossipAppService;

import com.co.kc.imchat.broker.interfaces.listener.AbstractRegistryEventListener;
import com.co.kc.imchat.broker.model.cqrs.event.GatewayHeartbeatEvent;
import com.co.kc.imchat.broker.model.cqrs.event.GatewayRegisteredEvent;
import com.co.kc.imchat.broker.model.cqrs.event.GatewayRemovedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Gateway 注册表事件监听器。
 * <p>
 * 将 gateway 注册、心跳、移除事件同步到 broker gossip 状态。
 */
@Component
public class GatewayRegistryEventListener extends AbstractRegistryEventListener {
    private final BrokerGossipAppService gossipService;

    public GatewayRegistryEventListener(BrokerGossipAppService gossipService) {
        this.gossipService = gossipService;
    }

    @EventListener
    public void onGatewayRegistered(GatewayRegisteredEvent event) {
        handle(() -> gossipService.observeGatewayRegistration(event), "gateway registered");
    }

    @EventListener
    public void onGatewayHeartbeat(GatewayHeartbeatEvent event) {
        handle(() -> gossipService.observeGatewayHeartbeat(event), "gateway heartbeat");
    }

    @EventListener
    public void onGatewayRemoved(GatewayRemovedEvent event) {
        handle(() -> gossipService.observeGatewayRemoval(event), "gateway removed");
    }
}
