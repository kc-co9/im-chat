package com.co.kc.imchat.broker.interfaces.listener.connection;

import com.co.kc.imchat.broker.application.gossip.BrokerGossipAppService;

import com.co.kc.imchat.broker.interfaces.listener.AbstractRegistryEventListener;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionRegisteredEvent;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionRemovedEvent;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionSyncedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 用户连接注册表事件监听器。
 * <p>
 * 将用户连接注册、移除和快照同步事件写入 broker gossip 状态。
 */
@Component
public class ConnectionRegistryEventListener extends AbstractRegistryEventListener {
    private final BrokerGossipAppService gossipService;

    public ConnectionRegistryEventListener(BrokerGossipAppService gossipService) {
        this.gossipService = gossipService;
    }

    @EventListener
    public void onConnectionRegistered(ConnectionRegisteredEvent event) {
        handle(() -> gossipService.observeConnectionRegistration(event), "connection registered");
    }

    @EventListener
    public void onConnectionRemoved(ConnectionRemovedEvent event) {
        handle(() -> gossipService.observeConnectionRemoval(event), "connection removed");
    }

    @EventListener
    public void onConnectionSynced(ConnectionSyncedEvent event) {
        handle(() -> gossipService.observeConnectionSnapshot(event), "connection snapshot updated");
    }
}
