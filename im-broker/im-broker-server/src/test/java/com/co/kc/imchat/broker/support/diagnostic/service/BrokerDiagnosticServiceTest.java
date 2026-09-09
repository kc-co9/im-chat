package com.co.kc.imchat.broker.support.diagnostic.service;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionMigrationRecordDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.enums.DiagnosticStatus;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.GossipRecordDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerOverviewDTO;
import com.co.kc.imchat.broker.support.diagnostic.tracker.impl.ConnectionMigrationTracker;
import com.co.kc.imchat.broker.support.diagnostic.tracker.impl.GossipSyncTracker;
import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.broker.memory.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.memory.InMemoryGatewayRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerDiagnosticServiceTest {
    private final InMemoryBrokerRegistry brokers = new InMemoryBrokerRegistry();
    private final InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
    private final InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
    private final GossipSyncTracker gossip = new GossipSyncTracker(100);
    private final ConnectionMigrationTracker migrations = new ConnectionMigrationTracker(100);
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
        gateways.register("gateway-1", "10.0.1.1", 12300);
        connections.register(1L, "gateway-1");
        gossip.track(new GossipRecordDTO(
                Instant.parse("2026-08-23T01:00:00Z"), "broker-2", DiagnosticStatus.SUCCESS, 2, 3L, null));
        migrations.track(new ConnectionMigrationRecordDTO(
                Instant.parse("2026-08-23T01:01:00Z"), "broker-2", DiagnosticStatus.FAILED, 1, 4L, "failed"));
        BrokerDiagnosticService service = service(brokers);

        BrokerOverviewDTO overview = service.overview();

        assertThat(overview.broker().id()).isEqualTo(properties.getInstance().getId());
        assertThat(overview.broker().address()).isEqualTo("10.0.0.1:12200");
        assertThat(overview.broker().startedAt()).isNotNull();
        assertThat(overview.broker().uptimeSeconds()).isNotNegative();
        assertThat(overview.statistics().brokerCount()).isEqualTo(1);
        assertThat(overview.statistics().gatewayCount()).isEqualTo(1);
        assertThat(overview.statistics().connectionCount()).isEqualTo(1);
        assertThat(overview.gossip().successCount()).isEqualTo(1);
        assertThat(overview.migration().failureCount()).isEqualTo(1);
        assertThat(service.brokers()).hasSize(1);
        assertThat(service.gateways()).hasSize(1);
        assertThat(service.connections(1L)).hasSize(1);
        assertThat(service.gossipRecords(1)).hasSize(1);
        assertThat(service.migrationRecords(1)).hasSize(1);
    }

    @Test
    void returnsEmptyStateAndRejectsInvalidRecordLimit() {
        BrokerDiagnosticService service = service(brokers);

        assertThat(service.brokers()).isEmpty();
        assertThat(service.gateways()).isEmpty();
        assertThat(service.connections(404L)).isEmpty();
        assertThatThrownBy(() -> service.gossipRecords(101))
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

    private BrokerDiagnosticService service(BrokerRegistry brokerRegistry) {
        return new BrokerDiagnosticService(
                brokerRegistry, gateways, connections, gossip, migrations, properties);
    }
}
