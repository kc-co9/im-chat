package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.domain.cluster.model.OperationFailureReason;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.model.cqrs.command.AdvanceClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerOperationReconcileCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteBrokerDrainCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceBrokerDrainCommand;
import com.co.kc.imchat.broker.model.cqrs.command.UpdateBrokerDrainProgressCommand;
import com.co.kc.imchat.broker.model.cqrs.result.BrokerDrainResult;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.support.BrokerClusterCommandFixture;
import com.co.kc.imchat.broker.support.BrokerClusterConsensusStub;
import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import com.co.kc.imchat.broker.support.TestClock;
import com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.checkpoint;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.summary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Broker drain 应用服务行为测试。 */
class BrokerDrainAppServiceTest {
    private final AtomicLong clock = new AtomicLong(2_100L);
    private MockedStatic<Instant> wallClock;

    @Test
    void drainCommandsUseExplicitRevisionWithoutReadingAdapterState() {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerClusterOperation operation = repository(true)
                .findOperation(new ClusterOperationId("drain-1")).orElseThrow();
        ClusterRevision revision =
                new ClusterRevision(42L);
        BrokerClusterRaftTransformer mapper =
                BrokerClusterRaftTransformer.INSTANCE;
        assertThat(mapper.drainCompletionCommandFrom(operation, revision,
                Instant.ofEpochMilli(2_100L)).expectedRevision()).isEqualTo(revision);
        assertThat(mapper.forcedDrainCommandFrom(operation, revision,
                new OperationFailureReason("accepted risk"),
                Instant.ofEpochMilli(2_100L)).expectedRevision()).isEqualTo(revision);
        verify(adapter, never()).getCommittedState();
    }

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
        assertThat(repository.getCommittedState().brokers()).filteredOn(
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
        assertThat(repository.getCommittedState().brokers()).filteredOn(
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
        long completedRevision = repository.getCommittedState().revision();
        assertThat(service.advanceDrain(command).toCompletableFuture().join().status())
                .isEqualTo(BrokerDrainResult.Status.SUCCEEDED);
        assertThat(repository.getCommittedState().revision()).isEqualTo(completedRevision);
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
                .when(repository).submitCommand(isA(UpdateBrokerDrainProgressCommand.class));
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.completedFuture(
                        new RouteCleanupResult(new RouteCount(2L), RouteCount.ZERO, true)),
                Duration.ofSeconds(10));

        BrokerDrainResult result = service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join();

        assertThat(result).isEqualTo(BrokerDrainResult.waitingForCleanup("evidence rejected"));
        verify(repository, never()).submitCommand(isA(CompleteBrokerDrainCommand.class));
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
    void gatewayTimeoutSubmitsTheSameTimeUsedForTheDecision() {
        TestRepository repository = spy(repository(false));
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.failedFuture(new AssertionError("cleanup must not run")),
                Duration.ofSeconds(10));
        clock.set(12_000L);
        wallClock.close();
        wallClock = TestClock.interceptNow(() -> clock.getAndAdd(1_000L));
        assertThat(service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join().status()).isEqualTo(BrokerDrainResult.Status.FAILED);
        ArgumentCaptor<AdvanceClusterOperationCommand> failure =
                ArgumentCaptor.forClass(AdvanceClusterOperationCommand.class);
        verify(repository).submitCommand(failure.capture());
        assertThat(failure.getValue().changedAt()).isEqualTo(Instant.ofEpochMilli(12_000L));
    }

    @Test
    void recordsCleanupFailureWhenRpcFailsAfterOperationTimeout() {
        clock.set(12_000L);
        TestRepository repository = spy(repository(true));
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.failedFuture(new IllegalStateException("cleanup unavailable")),
                Duration.ofSeconds(10));

        wallClock.close();
        wallClock = TestClock.interceptNow(() -> clock.getAndAdd(1_000L));
        BrokerDrainResult result = service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join();

        ArgumentCaptor<AdvanceClusterOperationCommand> failure =
                ArgumentCaptor.forClass(AdvanceClusterOperationCommand.class);
        verify(repository).submitCommand(failure.capture());
        assertThat(failure.getValue().changedAt()).isEqualTo(Instant.ofEpochMilli(13_000L));
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
                    .when(repository).submitCommand(isA(UpdateBrokerDrainProgressCommand.class));
        } else {
            doReturn(CompletableFuture.failedFuture(new IllegalStateException("evidence storage unavailable")))
                    .when(repository).submitCommand(isA(UpdateBrokerDrainProgressCommand.class));
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
                    .when(repository).submitCommand(isA(CompleteBrokerDrainCommand.class));
        } else {
            doReturn(CompletableFuture.failedFuture(new IllegalStateException("completion storage unavailable")))
                    .when(repository).submitCommand(isA(CompleteBrokerDrainCommand.class));
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitsWhenTimeoutFailureSubmissionIsRejected(boolean cleanupTimeout) {
        clock.set(12_000L);
        TestRepository repository = spy(repository(cleanupTimeout));
        BrokerClusterOperation original = repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow();
        BrokerClusterState originalState = repository.getCommittedState();
        doReturn(CompletableFuture.completedFuture(BrokerClusterConsensusResult.rejected("failure commit rejected")))
                .when(repository).submitCommand(isA(AdvanceClusterOperationCommand.class));
        AtomicInteger cleanupCalls = new AtomicInteger();
        BrokerClusterAppService service = service(repository, (target, operation) -> {
            cleanupCalls.incrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("cleanup unavailable"));
        }, Duration.ofSeconds(10));

        BrokerDrainResult result = service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join();

        assertThat(result).isEqualTo(cleanupTimeout
                ? BrokerDrainResult.waitingForCleanup("failure commit rejected")
                : BrokerDrainResult.waitingForGateways("failure commit rejected"));
        assertThat(cleanupCalls).hasValue(cleanupTimeout ? 1 : 0);
        assertThat(repository.findOperation(original.id()).orElseThrow()).isEqualTo(original);
        assertThat(repository.getCommittedState()).isEqualTo(originalState);
        verify(repository).submitCommand(isA(AdvanceClusterOperationCommand.class));
        verify(repository, never()).submitCommand(isA(UpdateBrokerDrainProgressCommand.class));
        verify(repository, never()).submitCommand(isA(CompleteBrokerDrainCommand.class));
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void propagatesTimeoutFailureSubmissionException(boolean cleanupTimeout, boolean synchronousFailure) {
        clock.set(12_000L);
        TestRepository repository = spy(repository(cleanupTimeout));
        BrokerClusterOperation original = repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow();
        IllegalStateException failure = new IllegalStateException("failure storage unavailable");
        if (synchronousFailure) {
            doThrow(failure).when(repository).submitCommand(isA(AdvanceClusterOperationCommand.class));
        } else {
            doReturn(CompletableFuture.failedFuture(failure))
                    .when(repository).submitCommand(isA(AdvanceClusterOperationCommand.class));
        }
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.failedFuture(new IllegalStateException("cleanup unavailable")),
                Duration.ofSeconds(10));

        assertThatThrownBy(() -> service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join()).satisfies(thrown -> {
                    if (synchronousFailure && !cleanupTimeout) {
                        assertThat(thrown).isSameAs(failure);
                    } else {
                        assertThat(thrown).hasCause(failure);
                    }
                });
        assertThat(repository.findOperation(original.id()).orElseThrow()).isEqualTo(original);
        verify(repository).submitCommand(isA(AdvanceClusterOperationCommand.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesConcurrentProgressWhenStaleTimeoutFailureIsRejected(boolean cleanupTimeout) {
        clock.set(12_000L);
        TestRepository repository = spy(repository(cleanupTimeout));
        CompletableFuture<BrokerClusterConsensusResult> failureSubmission = new CompletableFuture<>();
        doReturn(failureSubmission).when(repository).submitCommand(isA(AdvanceClusterOperationCommand.class));
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.failedFuture(new IllegalStateException("cleanup unavailable")),
                Duration.ofSeconds(10));

        CompletableFuture<BrokerDrainResult> pending = service.advanceDrain(
                new BrokerOperationReconcileCommand("drain-1")).toCompletableFuture();
        assertThat(pending).isNotDone();
        ArgumentCaptor<AdvanceClusterOperationCommand> staleFailure =
                ArgumentCaptor.forClass(AdvanceClusterOperationCommand.class);
        verify(repository).submitCommand(staleFailure.capture());
        if (!cleanupTimeout) {
            apply(repository, BrokerClusterCommandFixture.confirmDrainGateway(
                    "drain-1", "digest-1", "gateway-a", 12L, 3L, "checksum-a", 12_000L));
        }
        apply(repository, BrokerClusterCommandFixture.summarizeDrain(
                "drain-1", "digest-1", summary(3L, 2L, 0L, true), 12_000L));
        BrokerClusterOperation progressed = repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow();
        BrokerClusterState progressedState = repository.getCommittedState();
        assertThat(progressed.phase()).isEqualTo(BrokerClusterOperationPhase.VERIFYING);
        BrokerClusterConsensusResult rejected = repository.stateMachine.apply(staleFailure.getValue());
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.message()).isNotBlank();
        failureSubmission.complete(rejected);

        assertThat(pending.join()).isEqualTo(cleanupTimeout
                ? BrokerDrainResult.waitingForCleanup(rejected.message())
                : BrokerDrainResult.waitingForGateways(rejected.message()));
        assertThat(repository.findOperation(progressed.id()).orElseThrow()).isEqualTo(progressed);
        assertThat(repository.getCommittedState()).isEqualTo(progressedState);
        assertThat(service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join().status()).isEqualTo(BrokerDrainResult.Status.SUCCEEDED);
        assertThat(repository.findOperation(progressed.id()).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
    }

    @Test
    void returnsPersistedFailureWithoutSubmittingOrRepeatingCleanup() {
        TestRepository repository = spy(repository(false));
        apply(repository, BrokerClusterCommandFixture.advanceOperation(
                "drain-1", "digest-1", BrokerClusterOperationPhase.REBUILDING_ROUTES,
                BrokerClusterOperationPhase.FAILED, "persisted failure", 12_000L));
        BrokerClusterOperation failed = repository.findOperation(new ClusterOperationId("drain-1")).orElseThrow();
        BrokerClusterAppService service = service(repository,
                (target, operation) -> CompletableFuture.failedFuture(new AssertionError("cleanup must not run")),
                Duration.ofSeconds(10));
        clearInvocations(repository);

        assertThat(service.advanceDrain(new BrokerOperationReconcileCommand("drain-1"))
                .toCompletableFuture().join()).isEqualTo(BrokerDrainResult.failed("persisted failure"));
        assertThat(repository.findOperation(failed.id()).orElseThrow()).isEqualTo(failed);
        verify(repository, never()).submitCommand(any());
    }

    private BrokerClusterAppService service(TestRepository repository,
            BiFunction<BrokerNode, BrokerClusterOperation, CompletionStage<RouteCleanupResult>> cleanup,
            Duration drainOperationTimeout) {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        when(adapter.findOperation(any())).thenAnswer(
                invocation -> repository.findOperation(invocation.getArgument(0)));
        when(adapter.getCommittedState()).thenAnswer(ignored -> repository.getCommittedState());
        when(adapter.getClusterSnapshot()).thenAnswer(ignored ->
                BrokerClusterStateTransformer.INSTANCE
                        .brokerClusterFrom(repository.getCommittedState(), List.of(), null, 1000));
        BrokerRaftTestBindings.bindOperations(adapter, repository::submitCommand);
        BrokerPeerAdapter peerAdapter =
                mock(BrokerPeerAdapter.class);
        doAnswer(invocation -> {
            BrokerClusterOperation operation = invocation.getArgument(1);
            BrokerNode target = repository.getCommittedState().brokers().stream()
                    .filter(broker -> broker.brokerId().equals(operation.targetBrokerId().value()))
                    .findFirst().orElseThrow();
            return cleanup.apply(target, operation);
        }).when(peerAdapter).cleanupRoutes(any(), any());
        return new BrokerClusterAppService(adapter, peerAdapter, mock(GatewayRegistry.class),
                Duration.ofMillis(1), Duration.ofSeconds(10),
                mock(ScheduledExecutorService.class),
                drainOperationTimeout, null, null, Duration.ofSeconds(60));
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
        public Optional<BrokerClusterOperation> findActiveDrainOperation(RoutingEpoch targetEpoch) {
            return stateMachine.findActiveDrainOperation(targetEpoch);
        }

        @Override
        public CompletionStage<BrokerClusterConsensusResult> submitCommand(BrokerClusterConsensusCommand command) {
            return CompletableFuture.completedFuture(stateMachine.apply(command));
        }
    }
}
