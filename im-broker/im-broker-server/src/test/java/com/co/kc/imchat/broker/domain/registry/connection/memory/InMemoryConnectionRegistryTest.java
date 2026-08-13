package com.co.kc.imchat.broker.domain.registry.connection.memory;

import com.co.kc.imchat.broker.domain.registry.broker.memory.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionSyncResult;
import com.co.kc.imchat.broker.domain.registry.gateway.memory.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("realtime-behavior")
class InMemoryConnectionRegistryTest {

    @Test
    void registersOneConnectionForUser() {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry store = registries.connectionRegistry();
        InMemoryGatewayRegistry gatewayRegistry = registries.gatewayRegistry();

        gatewayRegistry.register("gw-1", "127.0.0.1", 9000);
        store.register(1L, "gw-1");

        List<UserGatewayDTO> locations = store.find(1L);
        assertEquals(1, locations.size());
        assertEquals("gw-1", locations.get(0).gatewayId());
    }

    @Test
    void registersMultipleConnectionsForUser() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();

        store.register(1L, "gw-1");
        store.register(1L, "gw-2");

        assertEquals(2, store.find(1L).size());
    }

    @Test
    void duplicateRegisterDoesNotKeepStaleConnection() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();

        store.register(1L, "gw-1");
        store.register(1L, "gw-1");

        assertEquals(1, store.find(1L).size());
        assertEquals(1, store.list().size());
    }

    @Test
    void unregistersOneConnectionOnly() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        store.register(1L, "gw-1");
        store.register(1L, "gw-2");

        store.unregister(1L, "gw-1");

        List<UserGatewayDTO> locations = store.find(1L);
        assertEquals(1, locations.size());
        assertEquals("gw-2", locations.get(0).gatewayId());
    }

    @Test
    void unregisterRemovesConnectionFromGatewayIndex() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        store.register(1L, "gw-1");

        store.unregister(1L, "gw-1");

        assertTrue(store.find(1L).isEmpty());
        assertTrue(store.list().isEmpty());
        assertTrue(store.sync("gw-1", List.of()).added().isEmpty());
        assertTrue(store.sync("gw-1", List.of()).removed().isEmpty());
    }

    @Test
    void sameGatewayCanHoldConnectionsForMultipleUsers() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        store.register(1L, "gw-1");

        store.register(2L, "gw-1");

        assertEquals(1, store.find(1L).size());
        assertEquals(1, store.find(2L).size());
    }

    @Test
    void unregisterIgnoresConnectionWhenCommandUserIsStale() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        store.register(1L, "gw-1");

        store.unregister(2L, "gw-1");

        assertEquals(1, store.find(1L).size());
    }

    @Test
    void unregisterGatewayRemovesEndpointOnly() {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry connectionRegistry = registries.connectionRegistry();
        InMemoryGatewayRegistry gatewayRegistry = registries.gatewayRegistry();
        gatewayRegistry.register("gw-1", "127.0.0.1", 9000);
        gatewayRegistry.register("gw-2", "127.0.0.2", 9000);
        connectionRegistry.register(1L, "gw-1");
        connectionRegistry.register(1L, "gw-2");

        gatewayRegistry.unregister("gw-1");

        assertTrue(gatewayRegistry.find("gw-1").isEmpty());
        assertTrue(gatewayRegistry.find("gw-2").isPresent());
        assertEquals(List.of("gw-1", "gw-2"), connectionRegistry.find(1L).stream()
                .map(UserGatewayDTO::gatewayId)
                .sorted()
                .toList());
    }

    @Test
    void syncReturnsAddedAndRemovedConnections() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();
        store.register(1L, "gw-1");
        store.register(2L, "gw-1");

        ConnectionSyncResult result = store.sync("gw-1", List.of(2L, 3L));

        assertEquals(List.of(3L), result.added().stream()
                .map(UserGatewayDTO::userId)
                .toList());
        assertEquals(List.of(1L), result.removed().stream()
                .map(UserGatewayDTO::userId)
                .toList());
    }

    @Test
    void heartbeatGatewayRefreshesLastSeenAtWithoutChangingRegisteredAt() {
        TestRegistries registries = testRegistries();
        InMemoryGatewayRegistry gatewayRegistry = registries.gatewayRegistry();
        gatewayRegistry.register("gw-1", "127.0.0.1", 9000);
        GatewayEndpointDTO before = gatewayRegistry.find("gw-1").orElseThrow();

        gatewayRegistry.heartbeat("gw-1");

        GatewayEndpointDTO after = gatewayRegistry.find("gw-1").orElseThrow();
        assertEquals(before.registeredAt(), after.registeredAt());
        assertTrue(after.lastSeenAt().isAfter(before.lastSeenAt()));
    }

    @Test
    void returnsEmptyWhenUserHasNoActiveConnections() {
        InMemoryConnectionRegistry store = new InMemoryConnectionRegistry();

        assertTrue(store.find(404L).isEmpty());
    }

    @Test
    void registersHeartbeatsAndUnregistersBrokerEndpoint() {
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        brokerRegistry.register("broker-1", "127.0.0.1", 12200);
        BrokerEndpointDTO before = brokerRegistry.find("broker-1").orElseThrow();

        brokerRegistry.heartbeat("broker-1");
        BrokerEndpointDTO after = brokerRegistry.find("broker-1").orElseThrow();

        assertEquals("127.0.0.1", after.host());
        assertEquals(12200, after.port());
        assertEquals(before.registeredAt(), after.registeredAt());
        assertTrue(after.lastSeenAt().isAfter(before.lastSeenAt()));
        assertEquals(List.of("broker-1"), brokerRegistry.list().stream()
                .map(BrokerEndpointDTO::brokerId)
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
