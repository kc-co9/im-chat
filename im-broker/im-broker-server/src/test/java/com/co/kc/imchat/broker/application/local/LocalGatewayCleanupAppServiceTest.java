package com.co.kc.imchat.broker.application.local;

import com.co.kc.imchat.broker.adapter.gossip.BrokerGossipAdapter;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.infrastructure.domain.cluster.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.transformer.registry.RegistryTransformer;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaOperation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LocalGatewayCleanupAppServiceTest {
    private static final GatewayId GATEWAY_ID = new GatewayId("gateway-process-1");
    private static final UserId USER_ID = new UserId(101L);

    @Test
    void explicitUnregisterRetainsLocalStateUntilGossipRemovalSucceeds() {
        InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        register(gateways, connections);
        BrokerGossipAdapter stateStore = mock(BrokerGossipAdapter.class);
        doThrow(new IllegalStateException("gossip unavailable"))
                .doNothing().when(stateStore).removeGatewayState(GATEWAY_ID.value());
        LocalGatewayCleanupAppService service = service(gateways, connections, stateStore);

        assertThatThrownBy(() -> service.unregister(GATEWAY_ID))
                .isInstanceOf(IllegalStateException.class);
        assertThat(gateways.find(GATEWAY_ID)).isPresent();
        assertThat(connections.find(USER_ID)).hasSize(1);

        assertThat(service.unregister(GATEWAY_ID)).hasSize(1);
        assertThat(gateways.find(GATEWAY_ID)).isEmpty();
        assertThat(connections.find(USER_ID)).isEmpty();
        verify(stateStore, times(2)).removeGatewayState(GATEWAY_ID.value());
    }

    @Test
    void failedGossipWriteLeavesGatewayRegisteredForNextCleanupRound() {
        InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        register(gateways, connections);
        BrokerGossipAdapter stateStore = mock(BrokerGossipAdapter.class);
        doThrow(new IllegalStateException("gossip unavailable"))
                .doNothing().when(stateStore).removeGatewayState(GATEWAY_ID.value());
        LocalGatewayCleanupAppService service = service(gateways, connections, stateStore);

        assertThatThrownBy(() -> service.cleanupExpired(Instant.now().plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(gateways.find(GATEWAY_ID)).isPresent();
        assertThat(connections.find(USER_ID)).hasSize(1);

        service.cleanupExpired(Instant.now().plusSeconds(10));

        assertThat(gateways.find(GATEWAY_ID)).isEmpty();
        assertThat(gateways.isRetired(GATEWAY_ID)).isTrue();
        assertThat(connections.find(USER_ID)).isEmpty();
        verify(stateStore, times(2)).removeGatewayState(GATEWAY_ID.value());
    }

    @Test
    void cleanupWritesGatewayAndConnectionTombstonesBeforeRemovingLocalRoutes() {
        InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        register(gateways, connections);
        BrokerGossipAdapter stateStore = new BrokerGossipAdapter(new InMemoryBrokerRegistry(), gateways,
                connections, new BrokerProperties(), new ClusterProperties());
        stateStore.putGatewayState(GATEWAY_ID.value(), "127.0.0.1", 18012, "test-identity");
        stateStore.putConnectionState(USER_ID.value(), GATEWAY_ID.value());

        LocalGatewayCleanupAppService service = service(gateways, connections, stateStore);
        service.cleanupExpired(Instant.now().plusSeconds(10));
        service.cleanupExpired(Instant.now().plusSeconds(10));

        assertThat(stateStore.deltas(List.of("GATEWAY:" + GATEWAY_ID.value())).getFirst().operation())
                .isEqualTo(GossipDeltaOperation.REMOVED);
        assertThat(stateStore.deltas(List.of("CONNECTION:101:" + GATEWAY_ID.value())).getFirst().operation())
                .isEqualTo(GossipDeltaOperation.REMOVED);
        assertThat(connections.find(USER_ID)).isEmpty();
        assertThat(gateways.find(GATEWAY_ID)).isEmpty();
    }

    private LocalGatewayCleanupAppService service(InMemoryGatewayRegistry gateways,
                                                  InMemoryConnectionRegistry connections,
                                                  BrokerGossipAdapter stateStore) {
        return new LocalGatewayCleanupAppService(gateways, connections, stateStore,
                Duration.ofSeconds(1), Duration.ofMillis(100));
    }

    private void register(InMemoryGatewayRegistry gateways, InMemoryConnectionRegistry connections) {
        gateways.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom(GATEWAY_ID.value(),
                "127.0.0.1", 18012, "test-identity"));
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(USER_ID, GATEWAY_ID),
                new RouteRevision(1L), Instant.MAX));
    }
}
