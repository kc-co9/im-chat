package com.co.kc.imchat.broker.application.local;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;

import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryBatchConnectionRegistry;
import com.co.kc.imchat.broker.support.RouteCacheStub;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.model.cqrs.command.CleanupDrainRoutesCommand;
import com.co.kc.imchat.broker.model.cqrs.dto.GatewayRouteWatermark;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import static com.co.kc.imchat.broker.support.BrokerRoutingPropertiesFixture.routingProperties;
import com.co.kc.imchat.broker.sdk.model.result.DrainRouteCleanupResult;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.co.kc.imchat.broker.support.BrokerDomainValues.checkpoint;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.gatewayId;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.routeCount;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.routeRevision;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.snapshotDigest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 从公开清理入口验证确认水位、幂等结果及缺失证据拒绝。 */
class LocalRouteAppServiceIdempotencyTest {
    private LocalRouteAppService createService(
            String brokerId, BrokerRaftAdapter consensus, LocalRoutingLeaseService lease,
            ConnectionRegistry connections, DomainEventPublisher publisher, BrokerRoutingProperties properties) {
        return new LocalRouteAppService(brokerId, consensus, lease,
                new InMemoryBatchConnectionRegistry(connections, properties,
                        RouteCacheStub.local(properties.getSnapshotBatchTimeout(), properties.getMaxPendingSnapshotBatches()),
                        RouteCacheStub.local(Duration.ofDays(3650), properties.getDrainCleanupResultCapacity())),
                publisher, properties.getSnapshotBatchTimeout());
    }

    @Test
    void purgesOnlyRoutesCoveredByCommittedGatewayWatermark() {
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gateway-a")), new RouteRevision(5L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(2L), new GatewayId("gateway-a")), new RouteRevision(8L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        LocalRouteAppService service = service(committedRepository(), connections);

        DrainRouteCleanupResult result = service.cleanupRoutes(command());

        assertThat(result.status()).isEqualTo(DrainRouteCleanupResult.Status.CLEANED);
        assertThat(result.removedOldRouteCount()).isEqualTo(1L);
        assertThat(result.residualOldRouteCount()).isEqualTo(1L);
        assertThat(connections.find(new UserId(1L))).isEmpty();
        assertThat(connections.find(new UserId(2L))).hasSize(1);
    }

    @Test
    void duplicateCommandReturnsSameResultWithoutRepeatingMutation() {
        InMemoryConnectionRegistry connections = spy(new InMemoryConnectionRegistry());
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gateway-a")), new RouteRevision(5L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        LocalRouteAppService service = service(committedRepository(), connections);

        DrainRouteCleanupResult first = service.cleanupRoutes(command());
        DrainRouteCleanupResult duplicate = service.cleanupRoutes(command());

        assertThat(first.removedOldRouteCount()).isEqualTo(1L);
        assertThat(duplicate).isEqualTo(first);
        verify(connections, times(1)).purgeRoutes(new GatewayId("gateway-a"), new RouteRevision(6L), Instant.ofEpochMilli(5_000L));
    }

    @Test
    void rejectsMissingCommittedDrainEvidenceWithoutRemovingRoutes() {
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gateway-a")), new RouteRevision(5L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        BrokerRaftAdapter repository = mock(BrokerRaftAdapter.class);
        when(repository.findOperation(new ClusterOperationId("drain-1"))).thenReturn(Optional.empty());

        DrainRouteCleanupResult result = service(repository, connections).cleanupRoutes(command());

        assertThat(result.status()).isEqualTo(DrainRouteCleanupResult.Status.REJECTED);
        assertThat(connections.find(new UserId(1L))).hasSize(1);
    }

    private LocalRouteAppService service(BrokerRaftAdapter repository, InMemoryConnectionRegistry connections) {
        return createService("broker-a", repository, mock(LocalRoutingLeaseService.class), connections,
                mock(DomainEventPublisher.class), routingProperties(1, 1024L, Duration.ofSeconds(1), 10));
    }

    private CleanupDrainRoutesCommand command() {
        return new CleanupDrainRoutesCommand("broker-leader", "drain-1", 7L, 8L, Instant.ofEpochMilli(5_000L),
                List.of(new GatewayRouteWatermark("gateway-a", 6L, 1L, snapshotDigest("gateway-a").value())));
    }

    private BrokerRaftAdapter committedRepository() {
        DrainProgress progress = new DrainProgress(List.of(checkpoint("gateway-a", 900L)),
                Instant.ofEpochMilli(5_000L), null).confirmGateway(gatewayId("gateway-a"),
                routeRevision(6L), routeCount(1L), snapshotDigest("gateway-a"));
        BrokerClusterOperation operation = BrokerClusterOperation.builder()
                .id(new ClusterOperationId("drain-1")).commandDigest(new CommandDigest("drain-digest"))
                .type(BrokerClusterOperationType.DRAIN_BROKER).targetBrokerId(new BrokerId("broker-a"))
                .acceptedEpoch(new RoutingEpoch(7L)).resultEpoch(new RoutingEpoch(8L))
                .acceptedRevision(new ClusterRevision(1L)).phase(BrokerClusterOperationPhase.REBUILDING_ROUTES)
                .createdAt(Instant.ofEpochMilli(1_000L)).drainProgress(progress).build();
        BrokerRaftAdapter repository = mock(BrokerRaftAdapter.class);
        when(repository.findOperation(operation.id())).thenReturn(Optional.of(operation));
        return repository;
    }
}
