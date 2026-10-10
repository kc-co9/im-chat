package com.co.kc.imchat.broker.application.diagnostic;

import com.co.kc.imchat.broker.transformer.infrastructure.RegistryTransformer;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;

import com.co.kc.imchat.broker.domain.connection.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.model.cqrs.query.GossipRecordsQuery;

import com.co.kc.imchat.broker.model.cqrs.query.ConnectionRoutesQuery;

import com.co.kc.imchat.broker.adapter.diagnostic.GossipSyncTracker;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.model.enums.DiagnosticStatus;
import com.co.kc.imchat.broker.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerOverviewDTO;
import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.cluster.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerDiagnosticAppServiceTest {
    private final InMemoryBrokerRegistry brokers = new InMemoryBrokerRegistry();
    private final InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
    private final InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
    private final GossipSyncTracker gossip = new GossipSyncTracker(100);
    private BrokerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BrokerProperties();
        properties.getInstance().setHost("10.0.0.1");
        properties.getInstance().setPort(12200);
    }

    @Test
    void buildsOverviewAndImmutableSnapshots() {
        brokers.register("broker-1", "10.0.0.1", 12200);
        gateways.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gateway-1", "10.0.1.1", 12300, "test-identity"));
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gateway-1")), new RouteRevision(0L), Instant.MAX));
        gossip.track(new GossipRecordDTO(
                Instant.parse("2026-08-23T01:00:00Z"), "broker-2", DiagnosticStatus.SUCCESS, 2, 3L, null));
        BrokerDiagnosticAppService service = service(brokers);

        BrokerOverviewDTO overview = service.overview();

        assertThat(overview.broker().id()).isEqualTo(properties.getInstance().getId());
        assertThat(overview.broker().address()).isEqualTo("10.0.0.1:12200");
        assertThat(overview.broker().startedAt()).isNotNull();
        assertThat(overview.broker().uptimeSeconds()).isNotNegative();
        assertThat(overview.statistics().brokerCount()).isEqualTo(1);
        assertThat(overview.statistics().gatewayCount()).isEqualTo(1);
        assertThat(overview.statistics().connectionCount()).isEqualTo(1);
        assertThat(overview.gossip().successCount()).isEqualTo(1);
        assertThat(service.brokers()).hasSize(1);
        assertThat(service.gateways()).hasSize(1);
        assertThat(service.connections(new ConnectionRoutesQuery(1L))).hasSize(1);
        assertThat(service.gossipRecords(new GossipRecordsQuery(1))).hasSize(1);
    }

    @Test
    void returnsEmptyStateAndRejectsInvalidRecordLimit() {
        BrokerDiagnosticAppService service = service(brokers);

        assertThat(service.brokers()).isEmpty();
        assertThat(service.gateways()).isEmpty();
        assertThat(service.connections(new ConnectionRoutesQuery(404L))).isEmpty();
        assertThatThrownBy(() -> service.gossipRecords(new GossipRecordsQuery(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doesNotForgeOverviewWhenRegistryAggregationFails() {
        BrokerRegistry failingRegistry = mock(BrokerRegistry.class);
        when(failingRegistry.list()).thenThrow(new IllegalStateException("registry unavailable"));

        assertThatThrownBy(() -> service(failingRegistry).overview())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("registry unavailable");
    }

    private BrokerDiagnosticAppService service(BrokerRegistry brokerRegistry) {
        return new BrokerDiagnosticAppService(
                brokerRegistry, gateways, connections, gossip,
                properties.getInstance().getId(), properties.getInstance().getAddress());
    }
}
