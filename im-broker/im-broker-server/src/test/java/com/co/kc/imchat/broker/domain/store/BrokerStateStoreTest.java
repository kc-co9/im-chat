package com.co.kc.imchat.broker.domain.store;

import com.co.kc.imchat.broker.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.domain.registry.broker.memory.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.memory.InMemoryGatewayRegistry;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaOperation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("realtime-behavior")
class BrokerStateStoreTest {

    @Test
    void mergesBrokerGatewayAndKeepsRemoteConnectionOutOfLocalRegistry() {
        TestNode source = testNode();
        TestNode target = testNode();
        source.brokerStateStore.putBrokerState("broker-1", "127.0.0.1", 12200);
        source.brokerStateStore.putGatewayState("gw-1", "127.0.0.1", 12201);
        source.brokerStateStore.putConnectionState(1L, "gw-1");

        List<String> keysToPull = source.brokerStateStore.keysNewerThan(target.brokerStateStore.digest());
        target.brokerStateStore.merge(source.brokerStateStore.deltas(keysToPull));

        assertEquals(List.of("broker-1"), target.brokerRegistry.list().stream()
                .map(endpoint -> endpoint.brokerId())
                .toList());
        assertTrue(target.gatewayRegistry.find("gw-1").isPresent());
        assertTrue(target.connectionRegistry.find(1L).isEmpty());
        assertTrue(target.brokerStateStore.keysNewerThan(List.of()).stream()
                .anyMatch(key -> key.equals("CONNECTION:1:gw-1")));
    }

    @Test
    void removedEntryRemovesConnection() {
        TestNode source = testNode();
        TestNode target = testNode();
        source.brokerStateStore.putConnectionState(1L, "gw-1");
        target.brokerStateStore.merge(source.brokerStateStore.deltas(
                source.brokerStateStore.keysNewerThan(target.brokerStateStore.digest())));

        source.brokerStateStore.removeConnectionState(1L, "gw-1");
        target.brokerStateStore.merge(source.brokerStateStore.deltas(
                source.brokerStateStore.keysNewerThan(target.brokerStateStore.digest())));

        assertTrue(target.connectionRegistry.find(1L).isEmpty());
    }

    @Test
    void removedGatewayCleansLocalConnectionsAndPublishesConnectionRemovedState() {
        TestNode source = testNode();
        TestNode target = testNode();
        target.gatewayRegistry.register("gw-1", "127.0.0.1", 12201);
        target.connectionRegistry.register(1L, "gw-1");
        target.connectionRegistry.register(2L, "gw-2");
        target.brokerStateStore.putConnectionState(1L, "gw-1");
        target.brokerStateStore.putConnectionState(2L, "gw-2");
        source.brokerStateStore.removeGatewayState("gw-1");

        target.brokerStateStore.merge(source.brokerStateStore.deltas(
                source.brokerStateStore.keysNewerThan(target.brokerStateStore.digest())));

        assertTrue(target.gatewayRegistry.find("gw-1").isEmpty());
        assertTrue(target.connectionRegistry.find(1L).isEmpty());
        assertEquals(List.of("gw-2"), target.connectionRegistry.find(2L).stream()
                .map(location -> location.gatewayId())
                .toList());
        assertTrue(target.brokerStateStore.deltas(target.brokerStateStore.keysNewerThan(source.brokerStateStore.digest()))
                .stream()
                .anyMatch(delta -> delta.key().equals("CONNECTION:1:gw-1")
                        && delta.operation() == GossipDeltaOperation.REMOVED));
    }

    private TestNode testNode() {
        return testNode(new ClusterProperties());
    }

    private TestNode testNode(ClusterProperties properties) {
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        InMemoryGatewayRegistry gatewayRegistry = new InMemoryGatewayRegistry();
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        BrokerStateStore brokerStateStore = new BrokerStateStore(
                brokerRegistry, gatewayRegistry, connectionRegistry, new BrokerProperties(), properties);
        return new TestNode(brokerStateStore, brokerRegistry, gatewayRegistry, connectionRegistry);
    }

    private record TestNode(BrokerStateStore brokerStateStore,
                            InMemoryBrokerRegistry brokerRegistry,
                            InMemoryGatewayRegistry gatewayRegistry,
                            InMemoryConnectionRegistry connectionRegistry) {
    }
}
