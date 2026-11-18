package com.co.kc.imchat.broker.infrastructure.registry.connection;

import com.co.kc.imchat.broker.transformer.registry.RegistryTransformer;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import java.time.Instant;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.infrastructure.domain.cluster.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionSyncResult;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRegistration;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("realtime-behavior")
class InMemoryConnectionRegistryTest {

    @Test
    void unifiedLookupPreservesObservationTimesAndDoesNotRebuildTheRegistration() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        Instant connectedAt = Instant.parse("2026-09-09T10:00:00Z");
        ConnectionRouteId id = new ConnectionRouteId(new UserId(42L), new GatewayId("gw-1"));
        ConnectionRegistration initial = ConnectionRegistration.builder()
                .id(id).revision(new RouteRevision(1L)).updateExpireAt(Instant.MAX)
                .connectedAt(connectedAt).refreshedAt(connectedAt).build();
        assertTrue(store.register(initial));
        assertThat(store.find(id.userId())).containsExactly(initial);
        assertThat(store.find(id.userId()).getFirst()).isSameAs(initial);

        assertTrue(store.register(ConnectionRegistration.active(id, new RouteRevision(2L), Instant.MAX)));
        ConnectionRegistration updated = store.find(id.userId()).getFirst();
        assertThat(updated.connectedAt()).isEqualTo(connectedAt);
        assertThat(updated.refreshedAt()).isAfter(connectedAt);
        assertThat(updated.revision()).isEqualTo(new RouteRevision(2L));
        assertThat(store.find(id.userId()).getFirst()).isSameAs(updated);
        assertThat(store.count()).isEqualTo(1L);
    }

    @Test
    void registersOneConnectionForUser() {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry store = registries.connectionRegistry();
        InMemoryGatewayRegistry gatewayRegistry = registries.gatewayRegistry();

        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "127.0.0.1", 9000, "test-identity"));
        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));

        List<ConnectionRegistration> locations = store.find(new UserId(1L));
        assertEquals(1, locations.size());
        assertEquals("gw-1", locations.get(0).gatewayId().value());
    }

    @Test
    void registersMultipleConnectionsForUser() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();

        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-2")), new RouteRevision(0L), Instant.MAX));

        assertEquals(2, store.find(new UserId(1L)).size());
    }

    @Test
    void duplicateRegisterDoesNotKeepStaleConnection() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();

        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));

        assertEquals(1, store.find(new UserId(1L)).size());
        assertEquals(1, store.list().size());
        assertEquals(1, store.count());
    }

    @Test
    void countsConnectionRoutesAcrossConcurrentUpdates() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        IntStream.range(0, 500).parallel()
                .forEach(index -> store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId((long) index), new GatewayId("gateway-" + index % 5)), new RouteRevision(0L), Instant.MAX)));

        assertEquals(500, store.count());

        IntStream.range(0, 250).parallel()
                .forEach(index -> store.unregister(new ConnectionRouteId(new UserId((long) index), new GatewayId("gateway-" + index % 5)), new RouteRevision(1L), Instant.MAX));

        assertEquals(250, store.count());
    }

    @Test
    void unregistersOneConnectionOnly() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-2")), new RouteRevision(0L), Instant.MAX));

        store.unregister(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(1L), Instant.MAX);

        List<ConnectionRegistration> locations = store.find(new UserId(1L));
        assertEquals(1, locations.size());
        assertEquals("gw-2", locations.get(0).gatewayId().value());
    }

    @Test
    void unregisterRemovesConnectionFromGatewayIndex() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));

        store.unregister(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(1L), Instant.MAX);

        assertTrue(store.find(new UserId(1L)).isEmpty());
        assertTrue(store.list().isEmpty());
        assertTrue(store.reconcileRoutes(new GatewayId("gw-1"), new RouteRevision(2L), Instant.MAX, List.of()).added().isEmpty());
        assertTrue(store.reconcileRoutes(new GatewayId("gw-1"), new RouteRevision(2L), Instant.MAX, List.of()).removed().isEmpty());
    }

    @Test
    void sameGatewayCanHoldConnectionsForMultipleUsers() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));

        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(2L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));

        assertEquals(1, store.find(new UserId(1L)).size());
        assertEquals(1, store.find(new UserId(2L)).size());
    }

    @Test
    void unregisterIgnoresConnectionWhenCommandUserIsStale() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));

        store.unregister(new ConnectionRouteId(new UserId(2L), new GatewayId("gw-1")), new RouteRevision(1L), Instant.MAX);

        assertEquals(1, store.find(new UserId(1L)).size());
    }

    @Test
    void unregisterGatewayRemovesEndpointOnly() {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry connectionRegistry = registries.connectionRegistry();
        InMemoryGatewayRegistry gatewayRegistry = registries.gatewayRegistry();
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "127.0.0.1", 9000, "test-identity"));
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-2", "127.0.0.2", 9000, "test-identity"));
        connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-2")), new RouteRevision(0L), Instant.MAX));

        gatewayRegistry.unregister(new GatewayId("gw-1"));

        assertTrue(gatewayRegistry.find(new GatewayId("gw-1")).isEmpty());
        assertTrue(gatewayRegistry.find(new GatewayId("gw-2")).isPresent());
        assertEquals(List.of("gw-1", "gw-2"), connectionRegistry.find(new UserId(1L)).stream()
                .map(registration -> registration.gatewayId().value())
                .sorted()
                .toList());
    }

    @Test
    void syncReturnsAddedAndRemovedConnections() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        store.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(2L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));

        ConnectionSyncResult result = store.reconcileRoutes(new GatewayId("gw-1"), new RouteRevision(2L), Instant.MAX, List.of(2L, 3L).stream().map(UserId::new).toList());

        assertEquals(List.of(3L), result.added().stream()
                .map(registration -> registration.userId().value())
                .toList());
        assertEquals(List.of(1L), result.removed().stream()
                .map(registration -> registration.userId().value())
                .toList());
    }

    @Test
    void heartbeatGatewayRefreshesLastSeenAtWithoutChangingRegisteredAt() {
        TestRegistries registries = testRegistries();
        InMemoryGatewayRegistry gatewayRegistry = registries.gatewayRegistry();
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "127.0.0.1", 9000, "test-identity"));
        GatewayRegistration before = gatewayRegistry.find(new GatewayId("gw-1")).orElseThrow();

        gatewayRegistry.heartbeat(new GatewayId("gw-1"));

        GatewayRegistration after = gatewayRegistry.find(new GatewayId("gw-1")).orElseThrow();
        assertEquals(before.registeredAt(), after.registeredAt());
        assertTrue(after.lastSeenAt().isAfter(before.lastSeenAt()));
    }

    @Test
    void returnsEmptyWhenUserHasNoActiveConnections() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();

        assertTrue(store.find(new UserId(404L)).isEmpty());
    }

    @Test
    void registersHeartbeatsAndUnregistersBrokerEndpoint() {
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        brokerRegistry.register("broker-1", "127.0.0.1", 12200);
        BrokerRegistration before = brokerRegistry.find("broker-1").orElseThrow();

        brokerRegistry.heartbeat("broker-1");
        BrokerRegistration after = brokerRegistry.find("broker-1").orElseThrow();

        assertEquals("127.0.0.1", after.host());
        assertEquals(12200, after.port());
        assertEquals(before.registeredAt(), after.registeredAt());
        assertTrue(after.lastSeenAt().isAfter(before.lastSeenAt()));
        assertEquals(List.of("broker-1"), brokerRegistry.list().stream()
                .map(BrokerRegistration::brokerId)
                .toList());

        brokerRegistry.unregister("broker-1");

        assertTrue(brokerRegistry.find("broker-1").isEmpty());
    }

    private TestRegistries testRegistries() {
        return new TestRegistries(new InMemoryConnectionRegistry(), new InMemoryGatewayRegistry());
    }

    private record TestRegistries(InMemoryConnectionRegistry connectionRegistry,
                                  InMemoryGatewayRegistry gatewayRegistry) {
    }
}
