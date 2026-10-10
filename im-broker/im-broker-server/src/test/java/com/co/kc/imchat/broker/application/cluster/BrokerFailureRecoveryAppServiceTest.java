package com.co.kc.imchat.broker.application.cluster;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.*;

import java.util.concurrent.ScheduledExecutorService;

import java.time.Duration;



import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

import com.co.kc.imchat.broker.model.cqrs.command.BrokerOperationReconcileCommand;
import com.co.kc.imchat.broker.support.BrokerClusterCommandFixture;

import com.co.kc.imchat.broker.model.cqrs.result.BrokerFailureRecoveryResult;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.model.cqrs.command.AdvanceClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.UpdateBrokerDrainProgressCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteRouteRecoveryCommand;
import com.co.kc.imchat.broker.support.TestClock;
import org.mockito.MockedStatic;
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

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.checkpoint;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.summary;

/** Broker 故障恢复应用服务行为测试。 */
class BrokerFailureRecoveryAppServiceTest {

    @Test
    void missingGatewayBeforeTimeoutDoesNotPersistSummaryOrDegradedIntent() {
        TestRepository repository = repository(false);
        BrokerClusterOperation before = repository.findOperation(new ClusterOperationId("failure-1")).orElseThrow();
        BrokerClusterAppService service = service(repository, Duration.ofSeconds(10));
        try (MockedStatic<Instant> clock = TestClock.interceptNow(() -> 1_500L)) {
            assertThat(service.recoverRoutes(new BrokerOperationReconcileCommand("failure-1"))
                    .toCompletableFuture().join().status())
                    .isEqualTo(BrokerFailureRecoveryResult.Status.WAITING_FOR_GATEWAYS);
        }
        assertThat(repository.findOperation(before.id())).contains(before);
    }

    @Test
    void rejectedSummaryDoesNotAdvanceOrCompleteDegradedRecovery() {
        TestRepository repository = repository(false);
        BrokerClusterOperation before = repository.findOperation(new ClusterOperationId("failure-1")).orElseThrow();
        repository.rejectSummarySubmission = true;
        BrokerClusterAppService service = service(repository, Duration.ofSeconds(10));

        BrokerFailureRecoveryResult result = service.recoverRoutes(new BrokerOperationReconcileCommand("failure-1"))
                .toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(BrokerFailureRecoveryResult.Status.WAITING_FOR_GATEWAYS);
        assertThat(repository.findOperation(before.id())).contains(before);
    }

    @Test
    void succeedsAfterEveryGatewayRebuildsAndOldEventsExpire() {
        TestRepository repository = repository(true);
        BrokerClusterAppService service = service(
                repository, Duration.ofSeconds(10));

        BrokerFailureRecoveryResult result = service.recoverRoutes(
                        new BrokerOperationReconcileCommand("failure-1"))
                .toCompletableFuture().join();

        assertThat(result.status())
                .isEqualTo(BrokerFailureRecoveryResult.Status.SUCCEEDED);
        assertThat(repository.findOperation(new ClusterOperationId("failure-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
    }

    @Test
    void recordsDegradedWhenFrozenGatewayEvidenceTimesOut() {
        TestRepository repository = repository(false);
        BrokerClusterAppService service = service(
                repository, Duration.ofSeconds(10));

        BrokerFailureRecoveryResult result = service.recoverRoutes(
                        new BrokerOperationReconcileCommand("failure-1"))
                .toCompletableFuture().join();

        assertThat(result.status())
                .isEqualTo(BrokerFailureRecoveryResult.Status.DEGRADED);
        assertThat(repository.findOperation(new ClusterOperationId("failure-1")).orElseThrow())
                .satisfies(operation -> {
                    assertThat(operation.phase())
                            .isEqualTo(BrokerClusterOperationPhase.DEGRADED);
                    assertThat(operation.error().value()).contains("gateway-a");
                });
    }

    @Test
    void resumesDegradedCompletionAfterRestartBetweenPhaseSubmissions() {
        TestRepository repository = repository(false);
        BrokerClusterAppService service = service(
                repository, Duration.ofSeconds(10));
        repository.interruptDegradedSubmission = true;

        assertThatThrownBy(() -> service.recoverRoutes(new BrokerOperationReconcileCommand("failure-1"))
                .toCompletableFuture().join()).hasCauseInstanceOf(IllegalStateException.class);
        BrokerClusterOperation interrupted = repository.findOperation(new ClusterOperationId("failure-1")).orElseThrow();
        assertThat(interrupted.phase()).isEqualTo(BrokerClusterOperationPhase.VERIFYING);
        assertThat(interrupted.error().value()).contains("gateway-a");
        // 即使提交方绕过恢复分支，日志应用边界也不能把持久化的降级意图覆盖为成功。
        BrokerClusterConsensusResult incorrectSuccess = repository.submitChange(
                BrokerClusterCommandFixture.advanceOperation("failure-1", "digest-1",
                        BrokerClusterOperationPhase.VERIFYING, BrokerClusterOperationPhase.SUCCEEDED,
                        null, Instant.now().toEpochMilli())).toCompletableFuture().join();
        assertThat(incorrectSuccess.accepted()).isFalse();
        assertThat(repository.findOperation(new ClusterOperationId("failure-1"))).contains(interrupted);

        TestRepository restored = repository(false);
        restored.stateMachine.restore(repository.stateMachine.snapshot());
        BrokerClusterAppService restarted = service(
                restored, Duration.ofSeconds(10));
        BrokerFailureRecoveryResult result = restarted.recoverRoutes(new BrokerOperationReconcileCommand("failure-1"))
                .toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(BrokerFailureRecoveryResult.Status.DEGRADED);
        assertThat(restored.findOperation(new ClusterOperationId("failure-1")).orElseThrow()).satisfies(operation -> {
            assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.DEGRADED);
            assertThat(operation.error()).isEqualTo(interrupted.error());
            assertThat(operation.drainProgress().recoveryEvidence()).isEqualTo(interrupted.drainProgress().recoveryEvidence());
        });
    }

    @Test
    void resumesNormalRecoveryFromEvidenceAfterOldEventsExpire() {
        TestRepository repository = repository(true);
        apply(repository, BrokerClusterCommandFixture.summarizeDrain(
                "failure-1", "digest-1", summary(3L, 0L, 0L, true), 1_500L));
        BrokerClusterAppService service = service(repository, Duration.ofSeconds(10));

        try (MockedStatic<Instant> clock = TestClock.interceptNow(() -> 1_500L)) {
            assertThat(service.recoverRoutes(new BrokerOperationReconcileCommand("failure-1"))
                    .toCompletableFuture().join().status())
                    .isEqualTo(BrokerFailureRecoveryResult.Status.WAITING_FOR_OLD_EVENTS);
        }
        try (MockedStatic<Instant> clock = TestClock.interceptNow(() -> 2_100L)) {
            assertThat(service.recoverRoutes(new BrokerOperationReconcileCommand("failure-1"))
                    .toCompletableFuture().join().status())
                    .isEqualTo(BrokerFailureRecoveryResult.Status.SUCCEEDED);
        }
        assertThat(repository.findOperation(new ClusterOperationId("failure-1")).orElseThrow())
                .satisfies(operation -> {
                    assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
                    assertThat(operation.drainProgress().recoveryEvidence()).isEqualTo(summary(3L, 0L, 0L, true));
                });
    }

    @Test
    void resumesDegradedRecoveryWithoutResubmittingPersistedEvidence() {
        TestRepository repository = repository(false);
        apply(repository, BrokerClusterCommandFixture.summarizeDrain(
                "failure-1", "digest-1", summary(0L, 0L, 0L, false), 12_000L));
        repository.rejectSummarySubmission = true;
        BrokerClusterAppService service = service(repository, Duration.ofSeconds(10));

        try (MockedStatic<Instant> clock = TestClock.interceptNow(() -> 12_000L)) {
            assertThat(service.recoverRoutes(new BrokerOperationReconcileCommand("failure-1"))
                    .toCompletableFuture().join().status())
                    .isEqualTo(BrokerFailureRecoveryResult.Status.DEGRADED);
        }
        assertThat(repository.findOperation(new ClusterOperationId("failure-1")).orElseThrow())
                .satisfies(operation -> {
                    assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.DEGRADED);
                    assertThat(operation.error().value()).contains("gateway-a");
                    assertThat(operation.drainProgress().recoveryEvidence()).isEqualTo(summary(0L, 0L, 0L, false));
                });
    }

    @Test
    void unavailableCompletionKeepsWaitingForVerifiedOperation() {
        TestRepository repository = repository(false);
        repository.rejectCompletionSubmission = true;
        BrokerClusterAppService service = service(repository, Duration.ofSeconds(10));

        BrokerFailureRecoveryResult result = service.recoverRoutes(new BrokerOperationReconcileCommand("failure-1"))
                .toCompletableFuture().join();

        assertThat(result).isEqualTo(BrokerFailureRecoveryResult.waitingForOldEvents("completion unavailable"));
        assertThat(repository.findOperation(new ClusterOperationId("failure-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.VERIFYING);
    }

    private BrokerClusterAppService service(TestRepository repository, Duration drainOperationTimeout) {
        BrokerClusterAdapter adapter = mock(BrokerClusterAdapter.class);
        org.mockito.Mockito.doCallRealMethod().when(adapter).saveRecoveryEvidence(any(), any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).completeDrain(any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).forceDrain(any(), any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).startEpochAuthorization(any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).completeEpochAuthorization(any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).verifyRouteRecovery(any(), any(), any());
        org.mockito.Mockito.doCallRealMethod().when(adapter).completeRouteRecovery(any(), any());
        when(adapter.findOperation(any())).thenAnswer(
                invocation -> repository.findOperation(invocation.getArgument(0)));
        when(adapter.committedState()).thenAnswer(ignored -> repository.committedState());
        when(adapter.submitChange(any())).thenAnswer(
                invocation -> repository.submitChange(invocation.getArgument(0)));
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
                "failure-1", "digest-1", 0L, "FAIL_BROKER", "broker-b",
                List.of(node("broker-a", BrokerRoutingState.ACTIVE),
                        node("broker-b", BrokerRoutingState.INACTIVE)), 1_000L,
                new DrainProgress(List.of(
                        checkpoint("gateway-a", 900L)),
                        Instant.ofEpochMilli(2_000L), null))))
                .satisfies(result -> assertThat(result.accepted()).isTrue());
        apply(repository, BrokerClusterCommandFixture.advanceOperation(
                "failure-1", "digest-1", BrokerClusterOperationPhase.RAFT_COMMITTED,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, 1_100L));
        apply(repository, BrokerClusterCommandFixture.advanceOperation(
                "failure-1", "digest-1", BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH,
                BrokerClusterOperationPhase.REBUILDING_ROUTES, null, 1_200L));
        if (confirmGateway) {
            apply(repository, BrokerClusterCommandFixture.confirmDrainGateway(
                    "failure-1", "digest-1", "gateway-a", 12L, 3L,
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
        private boolean interruptDegradedSubmission;
        private boolean rejectSummarySubmission;
        private boolean rejectCompletionSubmission;

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
        public CompletionStage<BrokerClusterConsensusResult> submitChange(BrokerClusterConsensusCommand command) {
            if (rejectCompletionSubmission && command instanceof CompleteRouteRecoveryCommand) {
                return CompletableFuture.completedFuture(BrokerClusterConsensusResult.unavailable(null, "completion unavailable"));
            }
            if (rejectSummarySubmission && command instanceof UpdateBrokerDrainProgressCommand) {
                return CompletableFuture.completedFuture(BrokerClusterConsensusResult.rejected("summary unavailable"));
            }
            if (interruptDegradedSubmission && command instanceof CompleteRouteRecoveryCommand complete
                    && stateMachine.findOperation(complete.operationId()).orElseThrow().error() != null) {
                return CompletableFuture.failedFuture(new IllegalStateException("leader stopped before degraded submission"));
            }
            return CompletableFuture.completedFuture(stateMachine.apply(command));
        }
    }
}
