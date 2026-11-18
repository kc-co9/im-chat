package com.co.kc.imchat.broker.adapter.gossip;

import com.co.kc.imchat.broker.transformer.registry.RegistryTransformer;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import java.time.Instant;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.domain.cluster.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaOperation;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaEntry;
import com.co.kc.imchat.common.utils.JsonUtils;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("realtime-behavior")
class BrokerGossipAdapterTest {

    @Test
    void mergesBrokerGatewayAndKeepsRemoteConnectionOutOfLocalRegistry() {
        TestNode source = testNode();
        TestNode target = testNode();
        source.brokerGossipAdapter.putBrokerState("broker-1", "127.0.0.1", 12200);
        source.brokerGossipAdapter.putGatewayState("gw-1", "127.0.0.1", 12201,
                "spiffe://im-chat/gateway/test");
        source.brokerGossipAdapter.putConnectionState(1L, "gw-1");

        List<String> keysToPull = source.brokerGossipAdapter.keysNewerThan(target.brokerGossipAdapter.digest());
        target.brokerGossipAdapter.merge(source.brokerGossipAdapter.deltas(keysToPull));

        assertEquals(List.of("broker-1"), target.brokerRegistry.list().stream()
                .map(endpoint -> endpoint.brokerId())
                .toList());
        assertTrue(target.gatewayRegistry.find(new GatewayId("gw-1")).isPresent());
        assertTrue(target.connectionRegistry.find(new UserId(1L)).isEmpty());
        assertTrue(target.brokerGossipAdapter.keysNewerThan(List.of()).stream()
                .anyMatch(key -> key.equals("CONNECTION:1:gw-1")));
    }

    @Test
    void removedEntryRemovesConnection() {
        TestNode source = testNode();
        TestNode target = testNode();
        source.brokerGossipAdapter.putConnectionState(1L, "gw-1");
        target.brokerGossipAdapter.merge(source.brokerGossipAdapter.deltas(
                source.brokerGossipAdapter.keysNewerThan(target.brokerGossipAdapter.digest())));

        source.brokerGossipAdapter.removeConnectionState(1L, "gw-1");
        target.brokerGossipAdapter.merge(source.brokerGossipAdapter.deltas(
                source.brokerGossipAdapter.keysNewerThan(target.brokerGossipAdapter.digest())));

        assertTrue(target.connectionRegistry.find(new UserId(1L)).isEmpty());
    }

    @Test
    void snapshotMarksOnlyMissingGatewayConnectionsAsRemoved() {
        TestNode node = testNode();
        node.brokerGossipAdapter.putConnectionState(1L, "gw-1");
        node.brokerGossipAdapter.putConnectionState(2L, "gw-1");
        node.brokerGossipAdapter.putConnectionState(3L, "gw-2");

        node.brokerGossipAdapter.updateConnectionSnapshot("gw-1", List.of(2L), List.of());

        assertEquals(GossipDeltaOperation.REMOVED,
                node.brokerGossipAdapter.deltas(List.of("CONNECTION:1:gw-1")).getFirst().operation());
        assertEquals(GossipDeltaOperation.ADDED,
                node.brokerGossipAdapter.deltas(List.of("CONNECTION:2:gw-1")).getFirst().operation());
        assertEquals(GossipDeltaOperation.ADDED,
                node.brokerGossipAdapter.deltas(List.of("CONNECTION:3:gw-2")).getFirst().operation());
    }

    @Test
    void connectionObservationUsesDomainIdentityValidation() {
        TestNode node = testNode();
        assertThatThrownBy(() -> node.brokerGossipAdapter.putConnectionState(null, "gw-1"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("用户ID不能为空");
        assertThatThrownBy(() -> node.brokerGossipAdapter.removeConnectionState(1L, " "))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("gatewayId must not be blank");
        assertTrue(node.brokerGossipAdapter.digest().isEmpty());
    }

    @Test
    void sameUserOnDifferentGatewaysRetainsWireIdentityAndIndependentSnapshots() {
        TestNode node = testNode();
        node.brokerGossipAdapter.putConnectionState(1L, "gw:1");
        node.brokerGossipAdapter.putConnectionState(1L, "gw:2");

        node.brokerGossipAdapter.updateConnectionSnapshot("gw:1", List.of(), List.of());

        GossipDeltaEntry removed = node.brokerGossipAdapter.deltas(List.of("CONNECTION:1:gw:1")).getFirst();
        assertEquals(GossipDeltaOperation.REMOVED, removed.operation());
        assertEquals(Map.of("userId", 1, "gatewayId", "gw:1"), JsonUtils.fromJson(removed.payload(), Map.class));
        assertEquals(GossipDeltaOperation.ADDED,
                node.brokerGossipAdapter.deltas(List.of("CONNECTION:1:gw:2")).getFirst().operation());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"null", "not-json", "{}"})
    void snapshotRecoversMissingOrInvalidPayloadFromConnectionIdentity(String payload) {
        TestNode source = testNode();
        TestNode target = testNode();
        source.brokerGossipAdapter.putConnectionState(1L, "gw:1");
        GossipDeltaEntry entry = source.brokerGossipAdapter.deltas(List.of("CONNECTION:1:gw:1")).getFirst();
        target.brokerGossipAdapter.merge(List.of(new GossipDeltaEntry(entry.key(), entry.version(),
                entry.entityType(), entry.operation(), payload)));

        target.brokerGossipAdapter.updateConnectionSnapshot("gw:1", List.of(), List.of());

        GossipDeltaEntry removed = target.brokerGossipAdapter.deltas(List.of(entry.key())).getFirst();
        assertEquals(GossipDeltaOperation.REMOVED, removed.operation());
        assertEquals(Map.of("userId", 1, "gatewayId", "gw:1"), JsonUtils.fromJson(removed.payload(), Map.class));
    }

    @Test
    void remoteConnectionRemovalCannotEraseNewerLocalRegistration() {
        TestNode source = testNode();
        TestNode target = testNode();
        target.connectionRegistry.register(ConnectionRegistration.active(
                new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(7L), Instant.MAX));
        source.brokerGossipAdapter.putConnectionState(1L, "gw-1");
        target.brokerGossipAdapter.merge(source.brokerGossipAdapter.deltas(
                source.brokerGossipAdapter.keysNewerThan(target.brokerGossipAdapter.digest())));
        source.brokerGossipAdapter.removeConnectionState(1L, "gw-1");
        target.brokerGossipAdapter.merge(source.brokerGossipAdapter.deltas(
                source.brokerGossipAdapter.keysNewerThan(target.brokerGossipAdapter.digest())));

        assertEquals(new RouteRevision(7L), target.connectionRegistry.find(new UserId(1L)).getFirst().revision());
        assertTrue(target.brokerGossipAdapter.deltas(target.brokerGossipAdapter.keysNewerThan(List.of())).stream()
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
        target.brokerGossipAdapter.putConnectionState(1L, "gw-1");
        target.brokerGossipAdapter.putConnectionState(2L, "gw-2");
        source.brokerGossipAdapter.removeGatewayState("gw-1");

        target.brokerGossipAdapter.merge(source.brokerGossipAdapter.deltas(
                source.brokerGossipAdapter.keysNewerThan(target.brokerGossipAdapter.digest())));

        assertTrue(target.gatewayRegistry.find(new GatewayId("gw-1")).isEmpty());
        assertTrue(target.connectionRegistry.find(new UserId(1L)).isEmpty());
        assertEquals(List.of("gw-2"), target.connectionRegistry.find(new UserId(2L)).stream()
                .map(location -> location.gatewayId().value())
                .toList());
        assertTrue(target.brokerGossipAdapter.deltas(target.brokerGossipAdapter.keysNewerThan(source.brokerGossipAdapter.digest()))
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
        BrokerGossipAdapter brokerGossipAdapter = new BrokerGossipAdapter(
                brokerRegistry, gatewayRegistry, connectionRegistry, new BrokerProperties(), properties);
        return new TestNode(brokerGossipAdapter, brokerRegistry, gatewayRegistry, connectionRegistry);
    }

    private record TestNode(BrokerGossipAdapter brokerGossipAdapter,
                            InMemoryBrokerRegistry brokerRegistry,
                            InMemoryGatewayRegistry gatewayRegistry,
                            InMemoryConnectionRegistry connectionRegistry) {
    }
}
