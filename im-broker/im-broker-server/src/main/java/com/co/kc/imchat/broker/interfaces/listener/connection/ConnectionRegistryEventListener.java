package com.co.kc.imchat.broker.interfaces.listener.connection;

import com.co.kc.imchat.broker.interfaces.listener.AbstractRegistryEventListener;
import com.co.kc.imchat.broker.support.event.model.ConnectionRegisteredEvent;
import com.co.kc.imchat.broker.support.event.model.ConnectionRemovedEvent;
import com.co.kc.imchat.broker.support.event.model.ConnectionSyncedEvent;
import com.co.kc.imchat.broker.domain.store.BrokerStateStore;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 用户连接注册表事件监听器。
 * <p>
 * 将用户连接注册、移除和快照同步事件写入 broker gossip 状态。
 */
@Component
public class ConnectionRegistryEventListener extends AbstractRegistryEventListener {
    private final BrokerStateStore brokerStateStore;

    public ConnectionRegistryEventListener(BrokerStateStore brokerStateStore) {
        this.brokerStateStore = brokerStateStore;
    }

    @EventListener
    public void onConnectionRegistered(ConnectionRegisteredEvent event) {
        handle(() -> brokerStateStore.putConnectionState(event.userId(), event.gatewayId()),
                "connection registered");
    }

    @EventListener
    public void onConnectionRemoved(ConnectionRemovedEvent event) {
        handle(() -> brokerStateStore.removeConnectionState(event.userId(), event.gatewayId()),
                "connection removed");
    }

    @EventListener
    public void onConnectionSynced(ConnectionSyncedEvent event) {
        handle(() -> brokerStateStore.updateConnectionSnapshot(
                event.gatewayId(), event.userIds(), event.removedLocations()), "connection snapshot updated");
    }
}
