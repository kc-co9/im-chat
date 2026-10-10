package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.support.BrokerClusterCommandFixture;

import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.support.BrokerClusterConsensusStub;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.adapter.cluster.raft.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmGatewayRoutesCommand;
import com.co.kc.imchat.broker.sdk.model.result.GatewayRouteSnapshotAckResult;
import com.co.kc.imchat.broker.interfaces.handler.connection.GatewayRouteSnapshotAckHandler;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotAckParams;
import com.co.kc.imchat.broker.support.BrokerRequestChainTestSupport;
import com.co.kc.imchat.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import com.co.kc.imchat.broker.support.TestClock;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.ArgumentMatchers.any;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.checkpoint;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.routeRevision;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.snapshotDigest;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.summary;

class BrokerClusterAppServiceGatewayRoutesTest {
    private MockedStatic<Instant> wallClock;

    @BeforeEach
    void controlWallClock() {
        wallClock = TestClock.interceptNow(() -> 1_500L);
    }

    @AfterEach
    void releaseWallClock() {
        wallClock.close();
    }

    @Test
    void commitsAggregateGatewayAckToMatchingDrainOperation() throws Exception {
        TestRepository repository = drainingRepository();
        BrokerClusterAppService service = service(repository);

        GatewayRouteSnapshotAckHandler handler = new GatewayRouteSnapshotAckHandler(
                service);
        GatewayRouteSnapshotAckResult result = (GatewayRouteSnapshotAckResult) BrokerRequestChainTestSupport.passThroughChain(handler).handle(null, JsonUtils.toJson(
                new GatewayRouteSnapshotAckParams(
                        "gateway-a", 5L, 12L, 3L,
                        snapshotDigest("checksum-a").value(), 2_000L)));

        assertThat(result.status())
                .isEqualTo(GatewayRouteSnapshotAckResult.Status.ACKNOWLEDGED);
        assertThat(repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow()
                .drainProgress().gateways().getFirst().confirmedRouteRevision())
                .isEqualTo(routeRevision(12L));
    }

    @Test
    void rejectsLateAckAfterRouteSummaryIsCommitted() {
        TestRepository repository = drainingRepository();
        BrokerClusterAppService service = service(repository);
        service.confirmGatewayRoutes(new ConfirmGatewayRoutesCommand(
                "gateway-a", 5L, 12L, 3L, snapshotDigest("checksum-a").value(), 2_000L));
        repository.submitChange(BrokerClusterCommandFixture.summarizeDrain(
                "drain-1", "digest-1", summary(3L, 3L, 0L, true), 1_600L));
        BrokerClusterOperation beforeAck = repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow();

        GatewayRouteSnapshotAckResult result = service.confirmGatewayRoutes(
                new ConfirmGatewayRoutesCommand(
                        "gateway-a", 5L, 13L, 3L,
                        snapshotDigest("changed-checksum").value(), 2_000L));

        assertThat(result.status())
                .isEqualTo(GatewayRouteSnapshotAckResult.Status.NOT_REQUIRED);
        assertThat(repository.findOperation(new ClusterOperationId("drain-1"))).contains(beforeAck);
    }

    @Test
    void ignoresEpochWithoutDrainAndRejectsExpiredAck() {
        TestRepository repository = drainingRepository();
        BrokerClusterAppService service = service(repository);

        assertThat(service.confirmGatewayRoutes(new ConfirmGatewayRoutesCommand(
                "gateway-a", 6L, 12L, 3L, snapshotDigest("checksum-a").value(),
                2_000L)).status())
                .isEqualTo(GatewayRouteSnapshotAckResult.Status.NOT_REQUIRED);
        assertThat(service.confirmGatewayRoutes(new ConfirmGatewayRoutesCommand(
                "gateway-a", 5L, 12L, 3L, snapshotDigest("checksum-a").value(),
                1_500L)).status())
                .isEqualTo(GatewayRouteSnapshotAckResult.Status.REJECTED);
    }

    private TestRepository drainingRepository() {
        BrokerClusterState initial = new BrokerClusterState(0L, 4L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(node("broker-a", BrokerRoutingState.ACTIVE),
                        node("broker-b", BrokerRoutingState.ACTIVE)));
        TestRepository repository = new TestRepository(initial);
        BrokerClusterCommandFixture.applyTopologyChange(repository,
                BrokerClusterCommandFixture.topology(
                "drain-1", "digest-1", 0L, "DRAIN_BROKER", "broker-b",
                List.of(node("broker-a", BrokerRoutingState.ACTIVE),
                        node("broker-b", BrokerRoutingState.DRAINING)), 1_000L,
                new DrainProgress(List.of(
                        checkpoint("gateway-a", 900L)),
                        Instant.ofEpochMilli(1_900L), null)));
        repository.submitChange(BrokerClusterCommandFixture.advanceOperation(
                "drain-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 1_100L));
        repository.submitChange(BrokerClusterCommandFixture.advanceOperation(
                "drain-1", "digest-1", BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH,
                BrokerClusterOperationPhase.REBUILDING_ROUTES, null, 1_200L));
        return repository;
    }

    private BrokerClusterAppService service(TestRepository repository) {
        BrokerClusterAdapter adapter = mock(BrokerClusterAdapter.class);
        doCallRealMethod().when(adapter).confirmGatewayRoutes(any(), any(), any());
        when(adapter.findActiveRouteRecoveryOperation(any())).thenAnswer(
                invocation -> repository.findActiveRouteRecoveryOperation(invocation.getArgument(0)));
        when(adapter.submitChange(any())).thenAnswer(
                invocation -> repository.submitChange(invocation.getArgument(0)));
        return new BrokerClusterAppService(adapter, mock(GatewayRegistry.class),
                Duration.ofMillis(1), Duration.ofSeconds(10),
                mock(ScheduledExecutorService.class), Duration.ofSeconds(10));
    }

    private BrokerNode node(String brokerId, BrokerRoutingState state) {
        return new BrokerNode(brokerId, brokerId + ":18020",
                BrokerMembershipRole.VOTER, state);
    }

    private static final class TestRepository implements BrokerClusterConsensusStub {
        private final RaftBrokerClusterStateMachine stateMachine;

        private TestRepository(BrokerClusterState initial) {
            stateMachine = new RaftBrokerClusterStateMachine("cluster-a", initial);
        }

        @Override
        public BrokerClusterState committedState() {
            return stateMachine.committedState();
        }

        @Override
        public Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
            return stateMachine.findOperation(operationId);
        }

        @Override
        public Optional<BrokerClusterOperation> findActiveDrainOperation(RoutingEpoch targetEpoch) {
            return stateMachine.findActiveDrainOperation(targetEpoch);
        }

        @Override
        public Optional<BrokerClusterOperation> findActiveRouteRecoveryOperation(
                RoutingEpoch targetEpoch) {
            return stateMachine.findActiveRouteRecoveryOperation(targetEpoch);
        }

        @Override
        public CompletionStage<BrokerClusterConsensusResult> submitChange(BrokerClusterConsensusCommand command) {
            return CompletableFuture.completedFuture(stateMachine.apply(command));
        }
    }
}
