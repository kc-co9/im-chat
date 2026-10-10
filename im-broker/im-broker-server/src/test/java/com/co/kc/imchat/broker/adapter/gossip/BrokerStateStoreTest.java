package com.co.kc.imchat.broker.adapter.gossip;

import com.co.kc.imchat.broker.transformer.infrastructure.RegistryTransformer;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import java.time.Instant;

import com.co.kc.imchat.broker.domain.connection.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.domain.cluster.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
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
        source.brokerStateStore.putGatewayState("gw-1", "127.0.0.1", 12201,
                "spiffe://im-chat/gateway/test");
        source.brokerStateStore.putConnectionState(1L, "gw-1");

        List<String> keysToPull = source.brokerStateStore.keysNewerThan(target.brokerStateStore.digest());
        target.brokerStateStore.merge(source.brokerStateStore.deltas(keysToPull));

        assertEquals(List.of("broker-1"), target.brokerRegistry.list().stream()
                .map(endpoint -> endpoint.brokerId())
                .toList());
        assertTrue(target.gatewayRegistry.find(new GatewayId("gw-1")).isPresent());
        assertTrue(target.connectionRegistry.find(new UserId(1L)).isEmpty());
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

        assertTrue(target.connectionRegistry.find(new UserId(1L)).isEmpty());
    }

    @Test
    void snapshotMarksOnlyMissingGatewayConnectionsAsRemoved() {
        TestNode node = testNode();
        node.brokerStateStore.putConnectionState(1L, "gw-1");
        node.brokerStateStore.putConnectionState(2L, "gw-1");
        node.brokerStateStore.putConnectionState(3L, "gw-2");

        node.brokerStateStore.updateConnectionSnapshot("gw-1", List.of(2L), List.of());

        assertEquals(GossipDeltaOperation.REMOVED,
                node.brokerStateStore.deltas(List.of("CONNECTION:1:gw-1")).getFirst().operation());
        assertEquals(GossipDeltaOperation.ADDED,
                node.brokerStateStore.deltas(List.of("CONNECTION:2:gw-1")).getFirst().operation());
        assertEquals(GossipDeltaOperation.ADDED,
                node.brokerStateStore.deltas(List.of("CONNECTION:3:gw-2")).getFirst().operation());
    }

    @Test
    void remoteConnectionRemovalCannotEraseNewerLocalRegistration() {
        TestNode source = testNode();
        TestNode target = testNode();
        target.connectionRegistry.register(ConnectionRegistration.active(
                new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(7L), Instant.MAX));
        source.brokerStateStore.putConnectionState(1L, "gw-1");
        target.brokerStateStore.merge(source.brokerStateStore.deltas(
                source.brokerStateStore.keysNewerThan(target.brokerStateStore.digest())));
        source.brokerStateStore.removeConnectionState(1L, "gw-1");
        target.brokerStateStore.merge(source.brokerStateStore.deltas(
                source.brokerStateStore.keysNewerThan(target.brokerStateStore.digest())));

        assertEquals(new RouteRevision(7L), target.connectionRegistry.find(new UserId(1L)).getFirst().revision());
        assertTrue(target.brokerStateStore.deltas(target.brokerStateStore.keysNewerThan(List.of())).stream()
                .anyMatch(delta -> delta.key().equals("CONNECTION:1:gw-1")
                        && delta.operation() == GossipDeltaOperation.REMOVED));
    }

    @Test
    void removedGatewayCleansLocalConnectionsAndPublishesConnectionRemovedState() {
        TestNode source = testNode();
        TestNode target = testNode();
        target.gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "127.0.0.1", 12201, "spiffe://im-chat/gateway/test"));
        target.connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        target.connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(2L), new GatewayId("gw-2")), new RouteRevision(0L), Instant.MAX));
        target.brokerStateStore.putConnectionState(1L, "gw-1");
        target.brokerStateStore.putConnectionState(2L, "gw-2");
        source.brokerStateStore.removeGatewayState("gw-1");

        target.brokerStateStore.merge(source.brokerStateStore.deltas(
                source.brokerStateStore.keysNewerThan(target.brokerStateStore.digest())));

        assertTrue(target.gatewayRegistry.find(new GatewayId("gw-1")).isEmpty());
        assertTrue(target.connectionRegistry.find(new UserId(1L)).isEmpty());
        assertEquals(List.of("gw-2"), target.connectionRegistry.find(new UserId(2L)).stream()
                .map(location -> location.gatewayId().value())
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
