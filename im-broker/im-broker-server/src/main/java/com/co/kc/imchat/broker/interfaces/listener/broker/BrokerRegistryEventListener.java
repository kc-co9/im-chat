package com.co.kc.imchat.broker.interfaces.listener.broker;

import com.co.kc.imchat.broker.interfaces.listener.AbstractRegistryEventListener;
import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.support.event.model.BrokerHeartbeatEvent;
import com.co.kc.imchat.broker.support.event.model.BrokerRegisteredEvent;
import com.co.kc.imchat.broker.support.event.model.BrokerRemovedEvent;
import com.co.kc.imchat.broker.domain.service.BrokerConnectionService;
import com.co.kc.imchat.broker.domain.store.BrokerStateStore;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Broker 注册表事件监听器。
 * <p>
 * 将 broker 注册、心跳、移除事件同步到 gossip 状态，并在节点变化后触发连接归属迁移。
 */
@Component
public class BrokerRegistryEventListener extends AbstractRegistryEventListener {
    private final BrokerRegistry brokerRegistry;
    private final BrokerStateStore brokerStateStore;
    private final BrokerConnectionService brokerConnectionService;

    public BrokerRegistryEventListener(BrokerRegistry brokerRegistry,
                                       BrokerStateStore brokerStateStore,
                                       BrokerConnectionService brokerConnectionService) {
        this.brokerRegistry = brokerRegistry;
        this.brokerStateStore = brokerStateStore;
        this.brokerConnectionService = brokerConnectionService;
    }

    @EventListener
    public void onBrokerRegistered(BrokerRegisteredEvent event) {
        handle(() -> brokerStateStore.putBrokerState(event.brokerId(), event.host(), event.port()),
                "broker registered");
        handle(brokerConnectionService::migrateConnection, "connection migration");
    }

    @EventListener
    public void onBrokerHeartbeat(BrokerHeartbeatEvent event) {
        handle(() -> brokerRegistry.find(event.brokerId())
                .ifPresent(endpoint -> brokerStateStore.putBrokerState(
                        endpoint.brokerId(), endpoint.host(), endpoint.port())), "broker heartbeat");
    }

    @EventListener
    public void onBrokerRemoved(BrokerRemovedEvent event) {
        handle(() -> brokerStateStore.removeBrokerState(event.brokerId()), "broker removed");
        handle(brokerConnectionService::migrateConnection, "connection migration");

    }
}
