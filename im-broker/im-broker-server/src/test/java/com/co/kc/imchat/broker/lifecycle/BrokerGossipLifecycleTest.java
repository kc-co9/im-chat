package com.co.kc.imchat.broker.lifecycle;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.domain.store.BrokerStateStore;
import com.co.kc.imchat.broker.domain.registry.broker.memory.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.memory.InMemoryGatewayRegistry;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.gossip.client.GossipPeerClient;
import com.co.kc.imchat.plugin.gossip.sync.GossipSynchronizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BrokerGossipLifecycleTest {

    @Test
    void skipsGossipWhenNoPeerExists() {
        ClusterProperties properties = new ClusterProperties();
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        BrokerStateStore brokerStateStore = gossipStateStore(properties, brokerRegistry);
        BoltInvoker boltInvoker = mock(BoltInvoker.class);
        BrokerGossipLifecycle lifecycle = new BrokerGossipLifecycle(
                brokerRegistry,
                new GossipSynchronizer(brokerStateStore, new GossipPeerClient(boltInvoker)),
                properties,
                brokerProperties("10.0.0.1", 12200));

        lifecycle.gossip();

        verifyNoInteractions(boltInvoker);
    }

    @Test
    void syncsDiscoveredBroker() {
        ClusterProperties properties = new ClusterProperties();
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        brokerRegistry.register("broker-2", "10.0.0.2", 12200);
        GossipSynchronizer gossipSynchronizer = mock(GossipSynchronizer.class);
        BrokerProperties brokerProperties = brokerProperties("10.0.0.1", 12200);
        BrokerGossipLifecycle lifecycle = new BrokerGossipLifecycle(
                brokerRegistry, gossipSynchronizer, properties, brokerProperties);

        lifecycle.gossip();

        verify(gossipSynchronizer).syncPeer(
                eq(brokerProperties.getInstance().getId()),
                eq("10.0.0.2:12200"),
                any(),
                eq(properties.getGossipTimeoutMillis()));
    }

    @Test
    void usesSeedAddressBeforeDiscoveringRemoteBroker() {
        ClusterProperties properties = new ClusterProperties();
        properties.setSeedAddresses(List.of("10.0.0.2:12200"));
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        GossipSynchronizer gossipSynchronizer = mock(GossipSynchronizer.class);
        BrokerProperties brokerProperties = brokerProperties("10.0.0.1", 12200);
        BrokerGossipLifecycle lifecycle = new BrokerGossipLifecycle(
                brokerRegistry, gossipSynchronizer, properties, brokerProperties);

        lifecycle.gossip();

        verify(gossipSynchronizer).syncPeer(
                eq(brokerProperties.getInstance().getId()),
                eq("10.0.0.2:12200"),
                any(),
                eq(properties.getGossipTimeoutMillis()));
    }

    private BrokerStateStore gossipStateStore(ClusterProperties properties,
                                               InMemoryBrokerRegistry brokerRegistry) {
        return new BrokerStateStore(
                brokerRegistry,
                new InMemoryGatewayRegistry(),
                new InMemoryConnectionRegistry(),
                new BrokerProperties(),
                properties);
    }

    private BrokerProperties brokerProperties(String host, int port) {
        BrokerProperties properties = new BrokerProperties();
        properties.getInstance().setHost(host);
        properties.getInstance().setPort(port);
        return properties;
    }
}
