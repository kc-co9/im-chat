package com.co.kc.imchat.broker.application.local;

import java.util.Map;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;

import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryBatchConnectionRegistry;
import com.co.kc.imchat.broker.support.RouteCacheStub;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

import com.co.kc.imchat.broker.support.BrokerClusterCommandFixture;

import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.support.BrokerClusterConsensusStub;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.model.cqrs.dto.GatewayRouteWatermark;
import com.co.kc.imchat.broker.model.cqrs.command.CleanupDrainRoutesCommand;
import com.co.kc.imchat.broker.sdk.model.result.DrainRouteCleanupResult;
import com.co.kc.imchat.broker.interfaces.handler.routing.DrainRouteCleanupHandler;
import com.co.kc.imchat.broker.sdk.model.params.DrainRouteCleanupParams;
import com.co.kc.imchat.broker.sdk.model.dto.DrainGatewayWatermarkDTO;
import com.co.kc.imchat.broker.support.BrokerRequestChainTestSupport;
import com.co.kc.imchat.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import static com.co.kc.imchat.broker.support.BrokerRoutingPropertiesFixture.routingProperties;
import java.time.Duration;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.checkpoint;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.snapshotDigest;

class LocalRouteAppServiceCleanupTest {
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
    void cleansOnlyCommittedWatermarksEvenAfterOldEventDeadline() throws Exception {
        TestRepository repository = repository();
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gateway-a")), new RouteRevision(5L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(2L), new GatewayId("gateway-a")), new RouteRevision(8L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        LocalRouteAppService service = createService(
                "broker-b", repository.asAdapter(), mock(LocalRoutingLeaseService.class), connections, mock(DomainEventPublisher.class), routingProperties(1, 1024L, Duration.ofSeconds(1), 10));

        DrainRouteCleanupHandler handler = new DrainRouteCleanupHandler(
                service);
        DrainRouteCleanupResult result = (DrainRouteCleanupResult) BrokerRequestChainTestSupport.passThroughChain(handler).handle(null, JsonUtils.toJson(new DrainRouteCleanupParams(
                "broker-a", "drain-1", 4L, 5L, 1_500L,
                List.of(new DrainGatewayWatermarkDTO(
                        "gateway-a", 7L, 1L, snapshotDigest("checksum-a").value())))));

        assertThat(result.status()).isEqualTo(DrainRouteCleanupResult.Status.CLEANED);
        assertThat(result.removedOldRouteCount()).isEqualTo(1L);
        assertThat(result.residualOldRouteCount()).isEqualTo(1L);
        assertThat(connections.find(new UserId(1L))).isEmpty();
        assertThat(connections.find(new UserId(2L))).hasSize(1);
    }

    @Test
    void rejectsWatermarkThatDoesNotMatchCommittedGatewayAck() {
        TestRepository repository = repository();
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        LocalRouteAppService service = createService(
                "broker-b", repository.asAdapter(), mock(LocalRoutingLeaseService.class), connections, mock(DomainEventPublisher.class), routingProperties(1, 1024L, Duration.ofSeconds(1), 10));

        DrainRouteCleanupResult result = service.cleanupRoutes(new CleanupDrainRoutesCommand(
                "broker-a", "drain-1", 4L, 5L, Instant.ofEpochMilli(1_500L),
                List.of(new GatewayRouteWatermark(
                        "gateway-a", 6L, 1L, snapshotDigest("changed").value()))));

        assertThat(result.status()).isEqualTo(DrainRouteCleanupResult.Status.REJECTED);
    }

    @Test
    void rejectsCleanupWithDeadlineDifferentFromCommittedOperation() {
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gateway-a")), new RouteRevision(5L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        LocalRouteAppService service = createService("broker-b", repository().asAdapter(), mock(LocalRoutingLeaseService.class), connections, mock(DomainEventPublisher.class), routingProperties(1, 1024L, Duration.ofSeconds(1), 10));

        DrainRouteCleanupResult result = service.cleanupRoutes(new CleanupDrainRoutesCommand(
                "broker-a", "drain-1", 4L, 5L, Instant.ofEpochMilli(1L),
                List.of(new GatewayRouteWatermark("gateway-a", 7L, 1L, snapshotDigest("checksum-a").value()))));

        assertThat(result.status()).isEqualTo(DrainRouteCleanupResult.Status.REJECTED);
        assertThat(connections.find(new UserId(1L))).hasSize(1);
    }

    @Test
    void rejectsDuplicateGatewayProofWithoutRemovingRoutes() {
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gateway-a")), new RouteRevision(5L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        LocalRouteAppService service = createService("broker-b", repository().asAdapter(),
                mock(LocalRoutingLeaseService.class), connections, mock(DomainEventPublisher.class),
                routingProperties(1, 1024L, Duration.ofSeconds(1), 10));
        GatewayRouteWatermark watermark = new GatewayRouteWatermark(
                "gateway-a", 7L, 1L, snapshotDigest("checksum-a").value());

        DrainRouteCleanupResult result = service.cleanupRoutes(new CleanupDrainRoutesCommand(
                "broker-a", "drain-1", 4L, 5L, Instant.ofEpochMilli(1_500L), List.of(watermark, watermark)));

        assertThat(result.status()).isEqualTo(DrainRouteCleanupResult.Status.REJECTED);
        assertThat(connections.find(new UserId(1L))).hasSize(1);
    }

    private TestRepository repository() {
        BrokerClusterState initial = new BrokerClusterState(0L, 4L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(node("broker-a", BrokerRoutingState.ACTIVE),
                        node("broker-b", BrokerRoutingState.ACTIVE)));
        TestRepository repository = new TestRepository(initial);
        assertThat(BrokerClusterCommandFixture.applyTopologyChange(
                repository, BrokerClusterCommandFixture.topology(
                "drain-1", "digest-1", 0L, "DRAIN_BROKER", "broker-b",
                List.of(node("broker-a", BrokerRoutingState.ACTIVE),
                        node("broker-b", BrokerRoutingState.DRAINING)), 1_000L,
                new DrainProgress(List.of(
                        checkpoint("gateway-a", 900L)),
                        Instant.ofEpochMilli(1_500L), null))))
                .satisfies(result -> assertThat(result.accepted()).isTrue());
        apply(repository, BrokerClusterCommandFixture.advanceOperation(
                "drain-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 1_100L));
        apply(repository, BrokerClusterCommandFixture.advanceOperation(
                "drain-1", "digest-1", BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH,
                BrokerClusterOperationPhase.REBUILDING_ROUTES, null, 1_200L));
        apply(repository, BrokerClusterCommandFixture.confirmDrainGateway(
                "drain-1", "digest-1", "gateway-a", 7L, 1L,
                "checksum-a", 1_300L));
        return repository;
    }

    private void apply(TestRepository repository, BrokerClusterConsensusCommand command) {
        assertThat(repository.submitCommand(command).toCompletableFuture().join().accepted()).isTrue();
    }

    private BrokerNode node(String brokerId, BrokerRoutingState state) {
        return new BrokerNode(brokerId, brokerId + ":18020",
                BrokerMembershipRole.VOTER, state);
    }

    private static final class TestRepository implements BrokerClusterConsensusStub {
        private final RaftBrokerClusterStateMachine stateMachine;

        private TestRepository(BrokerClusterState state) {
            stateMachine = new RaftBrokerClusterStateMachine("cluster-a", state, 1000, Map.of());
        }

        @Override
        public BrokerClusterState getCommittedState() {
            return stateMachine.committedState();
        }

        @Override
        public Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
            return stateMachine.findOperation(operationId);
        }

        @Override
        public CompletionStage<BrokerClusterConsensusResult> submitCommand(BrokerClusterConsensusCommand command) {
            return CompletableFuture.completedFuture(stateMachine.apply(command));
        }
    }
}
