package com.co.kc.imchat.broker.application.cluster;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.*;

import java.util.concurrent.ScheduledExecutorService;

import java.time.Duration;


import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;

import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

import com.co.kc.imchat.broker.model.cqrs.command.BrokerOperationReconcileCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceBrokerDrainCommand;
import com.co.kc.imchat.broker.support.BrokerClusterCommandFixture;

import com.co.kc.imchat.broker.model.cqrs.result.BrokerDrainResult;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.model.cqrs.command.UpdateBrokerDrainProgressCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteBrokerDrainCommand;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.support.BrokerClusterConsensusStub;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.adapter.cluster.raft.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import com.co.kc.imchat.broker.support.TestClock;
import java.util.concurrent.atomic.AtomicLong;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.checkpoint;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.summary;

/** Broker drain 应用服务行为测试。 */
class BrokerDrainAppServiceTest {
    private final AtomicLong clock = new AtomicLong(2_100L);
    private MockedStatic<Instant> wallClock;

    @BeforeEach
    void controlWallClock() {
        wallClock = TestClock.interceptNow(clock::get);
    }

    @AfterEach
    void releaseWallClock() {
        wallClock.close();
    }

    @Test
    void cleansConfirmedWatermarksAndCompletesDrainAfterOldDeadline() {
        TestRepository repository = repository(true);
        AtomicInteger cleanupCalls = new AtomicInteger();
        BiFunction<BrokerNode, BrokerClusterOperation, CompletionStage<RouteCleanupResult>> cleanupClient = (target, operation) -> {
            cleanupCalls.incrementAndGet();
            return CompletableFuture.completedFuture(
                    new RouteCleanupResult(new RouteCount(2L), RouteCount.ZERO, true));
        };
        BrokerClusterAppService service = service(
                repository, cleanupClient,
                Duration.ofSeconds(10));

        BrokerDrainResult result = service.advanceDrain(
                        new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(BrokerDrainResult.Status.SUCCEEDED);
        assertThat(cleanupCalls).hasValue(1);
        assertThat(repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
        assertThat(repository.committedState().brokers()).filteredOn(
                        broker -> broker.brokerId().equals("broker-b"))
                .extracting(BrokerNode::routingState)
                .containsExactly(BrokerRoutingState.INACTIVE);
    }

    @Test
    void doesNotCleanupBeforeEveryFrozenGatewayAcknowledges() {
        clock.set(1_500L);
        TestRepository repository = repository(false);
        AtomicInteger cleanupCalls = new AtomicInteger();
        BrokerClusterAppService service = service(
                repository, (target, operation) -> {
                    cleanupCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            new RouteCleanupResult(RouteCount.ZERO, RouteCount.ZERO, true));
                }, Duration.ofSeconds(10));

        BrokerDrainResult result = service.advanceDrain(
                        new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(BrokerDrainResult.Status.WAITING_FOR_GATEWAYS);
        assertThat(cleanupCalls).hasValue(0);
    }

    @Test
    void commitsFailedWhenGatewayEvidenceExceedsOperationTimeout() {
        clock.set(12_000L);
        TestRepository repository = repository(false);
        BrokerClusterAppService service = service(
                repository, (target, operation) -> CompletableFuture.failedFuture(
                new AssertionError("cleanup must not run")),
                Duration.ofSeconds(10));

        BrokerDrainResult result = service.advanceDrain(
                        new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(BrokerDrainResult.Status.FAILED);
        assertThat(repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.FAILED);

        BrokerDrainResult forced = service.forceDrain(new ForceBrokerDrainCommand(
                        "drain-1", "operator accepted missing Gateway evidence"))
                .toCompletableFuture().join();

        assertThat(forced.status()).isEqualTo(BrokerDrainResult.Status.FORCED);
        assertThat(repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow()).satisfies(operation -> {
            assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.FORCED);
            assertThat(operation.error().value()).contains("missing Gateway evidence");
        });
        assertThat(repository.committedState().brokers()).filteredOn(
                        broker -> broker.brokerId().equals("broker-b"))
                .extracting(BrokerNode::routingState)
                .containsExactly(BrokerRoutingState.INACTIVE);
    }

    @Test
    void resumesVerificationAndLetsAggregateHandleRepeatedCompletion() {
        TestRepository repository = repository(true);
        apply(repository, BrokerClusterCommandFixture.summarizeDrain(
                "drain-1", "digest-1", summary(3L, 2L, 0L, true), 2_100L));
        assertThat(repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.VERIFYING);
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.failedFuture(new AssertionError("cleanup must not repeat")),
                Duration.ofSeconds(10));
        BrokerOperationReconcileCommand command = new BrokerOperationReconcileCommand("drain-1");

        assertThat(service.advanceDrain(command).toCompletableFuture().join().status())
                .isEqualTo(BrokerDrainResult.Status.SUCCEEDED);
        long completedRevision = repository.committedState().revision();
        assertThat(service.advanceDrain(command).toCompletableFuture().join().status())
                .isEqualTo(BrokerDrainResult.Status.SUCCEEDED);
        assertThat(repository.committedState().revision()).isEqualTo(completedRevision);
    }

    @Test
    void resumesPersistedEvidenceAfterOldEventsExpireWithoutRepeatingCleanup() {
        clock.set(1_500L);
        TestRepository repository = repository(true);
        AtomicInteger cleanupCalls = new AtomicInteger();
        BrokerClusterAppService service = service(repository, (target, operation) -> {
            cleanupCalls.incrementAndGet();
            return CompletableFuture.completedFuture(new RouteCleanupResult(new RouteCount(2L), RouteCount.ZERO, true));
        }, Duration.ofSeconds(10));
        BrokerOperationReconcileCommand command = new BrokerOperationReconcileCommand("drain-1");

        assertThat(service.advanceDrain(command).toCompletableFuture().join().status())
                .isEqualTo(BrokerDrainResult.Status.WAITING_FOR_OLD_EVENTS);
        clock.set(2_100L);
        assertThat(service.advanceDrain(command).toCompletableFuture().join().status())
                .isEqualTo(BrokerDrainResult.Status.SUCCEEDED);
        assertThat(cleanupCalls).hasValue(1);
    }

    @Test
    void stopsWhenEvidenceSubmissionIsRejected() {
        TestRepository repository = spy(repository(true));
        doReturn(CompletableFuture.completedFuture(BrokerClusterConsensusResult.rejected("evidence rejected")))
                .when(repository).submitChange(isA(UpdateBrokerDrainProgressCommand.class));
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.completedFuture(
                        new RouteCleanupResult(new RouteCount(2L), RouteCount.ZERO, true)),
                Duration.ofSeconds(10));

        BrokerDrainResult result = service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join();

        assertThat(result).isEqualTo(BrokerDrainResult.waitingForCleanup("evidence rejected"));
        verify(repository, never()).submitChange(isA(CompleteBrokerDrainCommand.class));
        assertThat(repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.REBUILDING_ROUTES);
    }

    @Test
    void waitsForCleanupWhenRpcFailsBeforeOperationTimeout() {
        TestRepository repository = repository(true);
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.failedFuture(new IllegalStateException("cleanup unavailable")),
                Duration.ofSeconds(10));

        BrokerDrainResult result = service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join();

        assertThat(result).isEqualTo(BrokerDrainResult.waitingForCleanup("cleanup unavailable"));
        assertThat(repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.REBUILDING_ROUTES);
    }

    @Test
    void recordsCleanupFailureWhenRpcFailsAfterOperationTimeout() {
        clock.set(12_000L);
        TestRepository repository = repository(true);
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.failedFuture(new IllegalStateException("cleanup unavailable")),
                Duration.ofSeconds(10));

        BrokerDrainResult result = service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join();

        assertThat(result).isEqualTo(BrokerDrainResult.failed("drain cleanup timeout: cleanup unavailable"));
        assertThat(repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow()).satisfies(operation -> {
            assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.FAILED);
            assertThat(operation.error().value()).isEqualTo(result.message());
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void propagatesEvidenceSubmissionFailureInsteadOfRecordingCleanupTimeout(boolean synchronousFailure) {
        clock.set(12_000L);
        TestRepository repository = spy(repository(true));
        if (synchronousFailure) {
            doThrow(new IllegalStateException("evidence storage unavailable"))
                    .when(repository).submitChange(isA(UpdateBrokerDrainProgressCommand.class));
        } else {
            doReturn(CompletableFuture.failedFuture(new IllegalStateException("evidence storage unavailable")))
                    .when(repository).submitChange(isA(UpdateBrokerDrainProgressCommand.class));
        }
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.completedFuture(
                        new RouteCleanupResult(new RouteCount(2L), RouteCount.ZERO, true)),
                Duration.ofSeconds(10));

        assertThatThrownBy(() -> service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join()).hasRootCauseMessage("evidence storage unavailable");
        assertThat(repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.REBUILDING_ROUTES);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void propagatesCompletionFailureInsteadOfRecordingCleanupTimeout(boolean synchronousFailure) {
        clock.set(12_000L);
        TestRepository repository = spy(repository(true));
        if (synchronousFailure) {
            doThrow(new IllegalStateException("completion storage unavailable"))
                    .when(repository).submitChange(isA(CompleteBrokerDrainCommand.class));
        } else {
            doReturn(CompletableFuture.failedFuture(new IllegalStateException("completion storage unavailable")))
                    .when(repository).submitChange(isA(CompleteBrokerDrainCommand.class));
        }
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.completedFuture(
                        new RouteCleanupResult(new RouteCount(2L), RouteCount.ZERO, true)),
                Duration.ofSeconds(10));

        assertThatThrownBy(() -> service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join()).hasRootCauseMessage("completion storage unavailable");
        assertThat(repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.VERIFYING);
    }

    private BrokerClusterAppService service(TestRepository repository,
            BiFunction<BrokerNode, BrokerClusterOperation, CompletionStage<RouteCleanupResult>> cleanup,
            Duration drainOperationTimeout) {
        BrokerClusterAdapter adapter = mock(BrokerClusterAdapter.class);
        org.mockito.Mockito.doCallRealMethod().when(adapter).saveRecoveryEvidence(any(), any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).completeDrain(any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).forceDrain(any(), any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).startEpochAuthorization(any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).completeEpochAuthorization(any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).verifyRouteRecovery(any(), any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).completeRouteRecovery(any(), any());
        doCallRealMethod().when(adapter).failDrain(any(), any(), any());
        when(adapter.findOperation(any())).thenAnswer(
                invocation -> repository.findOperation(invocation.getArgument(0)));
        when(adapter.committedState()).thenAnswer(ignored -> repository.committedState());
        when(adapter.submitChange(any())).thenAnswer(
                invocation -> repository.submitChange(invocation.getArgument(0)));
        when(adapter.cleanupRoutes(any())).thenAnswer(invocation -> {
            BrokerClusterOperation operation = invocation.getArgument(0);
            BrokerNode target = repository.committedState().brokers().stream()
                    .filter(broker -> broker.brokerId().equals(operation.targetBrokerId().value()))
                    .findFirst().orElseThrow();
            return cleanup.apply(target, operation);
        });
        return new BrokerClusterAppService(adapter, mock(GatewayRegistry.class),
                Duration.ofMillis(1), Duration.ofSeconds(10),
                mock(ScheduledExecutorService.class),
                drainOperationTimeout);
    }

    private TestRepository repository(boolean confirmGateway) {
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
                        Instant.ofEpochMilli(2_000L), null))))
                .satisfies(result -> assertThat(result.accepted()).isTrue());
        apply(repository, BrokerClusterCommandFixture.advanceOperation(
                "drain-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 1_100L));
        apply(repository, BrokerClusterCommandFixture.advanceOperation(
                "drain-1", "digest-1", BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH,
                BrokerClusterOperationPhase.REBUILDING_ROUTES, null, 1_200L));
        if (confirmGateway) {
            apply(repository, BrokerClusterCommandFixture.confirmDrainGateway(
                    "drain-1", "digest-1", "gateway-a", 12L, 3L,
                    "checksum-a", 1_300L));
        }
        return repository;
    }

    private void apply(TestRepository repository, BrokerClusterConsensusCommand command) {
        assertThat(repository.submitChange(command).toCompletableFuture().join().accepted()).isTrue();
    }

    private BrokerNode node(String brokerId, BrokerRoutingState state) {
        return new BrokerNode(brokerId, brokerId + ":18020",
                BrokerMembershipRole.VOTER, state);
    }

    private static final class TestRepository implements BrokerClusterConsensusStub {
        private final RaftBrokerClusterStateMachine stateMachine;

        private TestRepository(BrokerClusterState state) {
            stateMachine = new RaftBrokerClusterStateMachine("cluster-a", state);
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
        public CompletionStage<BrokerClusterConsensusResult> submitChange(BrokerClusterConsensusCommand command) {
            return CompletableFuture.completedFuture(stateMachine.apply(command));
        }
    }
}
