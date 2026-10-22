package com.co.kc.imchat.broker.interfaces.listener.broker;

import com.co.kc.imchat.broker.application.gossip.BrokerGossipAppService;

import com.co.kc.imchat.broker.interfaces.listener.AbstractRegistryEventListener;
import com.co.kc.imchat.broker.model.cqrs.event.BrokerHeartbeatEvent;
import com.co.kc.imchat.broker.model.cqrs.event.BrokerRegisteredEvent;
import com.co.kc.imchat.broker.model.cqrs.event.BrokerRemovedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Broker 注册表事件监听器。
 * <p>
 * 将 Broker 注册、心跳和移除事件同步为 Gossip observation。
 */
@Component
public class BrokerRegistryEventListener extends AbstractRegistryEventListener {
    private final BrokerGossipAppService gossipService;

    public BrokerRegistryEventListener(BrokerGossipAppService gossipService) {
        this.gossipService = gossipService;
    }

    @EventListener
    public void onBrokerRegistered(BrokerRegisteredEvent event) {
        handle(() -> gossipService.observeBrokerRegistration(event), "broker registered");
    }

    @EventListener
    public void onBrokerHeartbeat(BrokerHeartbeatEvent event) {
        handle(() -> gossipService.observeBrokerHeartbeat(event), "broker heartbeat");
    }

    @EventListener
    public void onBrokerRemoved(BrokerRemovedEvent event) {
        handle(() -> gossipService.observeBrokerRemoval(event), "broker removed");
    }
}
