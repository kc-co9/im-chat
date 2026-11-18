package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.domain.cluster.service.MembershipOperationService;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipExecutionConfirmation;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryMembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryLearnerConfirmationAttemptRepository;

import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor;

import com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.cluster.model.*;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerPeerAdmissionInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.model.properties.BrokerGossipSettings;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.broker.interfaces.handler.broker.MembershipPermitDrainedHandler;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BrokerMembershipPermitAdmissionTest {
    @Test
    void pendingRemoteConfirmationsRejectExcessWaitersWithoutCallingApplication() throws Exception {
        BrokerMembershipAppService service = mock(BrokerMembershipAppService.class);
        ConfirmMembershipPermitDrainedParams fact = params(cluster(), "inc");
        MembershipConfirmationResult committed = confirmationCommitted();
        CompletableFuture<MembershipConfirmationResult> pending = new CompletableFuture<>();
        CountDownLatch entered = new CountDownLatch(1);
        when(service.submitExecutionConfirmation(acceptedFact(fact, "removed"))).thenAnswer(call -> {
            entered.countDown(); return pending;
        }).thenReturn(CompletableFuture.completedFuture(committed));
        MembershipPermitDrainedHandler handler = new MembershipPermitDrainedHandler(service, 1, 1000);
        CompletableFuture<Object> first = new CompletableFuture<>();
        Thread caller = Thread.ofPlatform().start(() -> {
            try { first.complete(handler.handleRequest(fact, invocation(fact, true).principal())); }
            catch (Throwable failure) { first.completeExceptionally(failure); }
        });
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> handler.handleRequest(fact, null)).isInstanceOf(SecurityException.class);
            assertThat(((com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult) handler.handleRequest(fact, invocation(fact, true).principal())).status())
                    .isEqualTo(com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult.Status.UNKNOWN);
            verify(service, times(1)).submitExecutionConfirmation(acceptedFact(fact, "removed"));
            pending.complete(committed);
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(
                    com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipPermitDrainedResponseFrom(committed));
            assertThat(handler.handleRequest(fact, invocation(fact, true).principal())).isEqualTo(
                    com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipPermitDrainedResponseFrom(committed));
        } finally {
            pending.complete(committed); caller.join(5000); assertThat(caller.isAlive()).isFalse();
        }
    }

    @Test
    void remoteConfirmationTimeoutReleasesOnlyRequestBudget() throws Exception {
        BrokerMembershipAppService service = mock(BrokerMembershipAppService.class);
        ConfirmMembershipPermitDrainedParams fact = params(cluster(), "inc");
        AtomicInteger timedWaits = new AtomicInteger();
        CompletableFuture<MembershipConfirmationResult> source = new CompletableFuture<>() {
            @Override
            public MembershipConfirmationResult join() { return confirmationCommitted(); }
            @Override
            public MembershipConfirmationResult get(long timeout, TimeUnit unit) throws TimeoutException {
                assertThat(unit).isEqualTo(TimeUnit.NANOSECONDS);
                assertThat(timeout).isPositive().isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(1000));
                timedWaits.incrementAndGet(); throw new TimeoutException("controlled request deadline");
            }
        };
        when(service.submitExecutionConfirmation(acceptedFact(fact, "removed"))).thenReturn(source);
        MembershipPermitDrainedHandler handler = new MembershipPermitDrainedHandler(service, 1, 1000);
        for (int request = 0; request < 2; request++) {
            assertThat(((com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult) handler.handleRequest(fact, invocation(fact, true).principal())).status())
                    .isEqualTo(com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult.Status.UNKNOWN);
        }
        assertThat(timedWaits).hasValue(2);
        assertThat(source).isNotDone().isNotCancelled();
        verify(service, times(2)).submitExecutionConfirmation(acceptedFact(fact, "removed"));
        source.complete(confirmationCommitted());
        verifyRealPendingConfirmationSurvivesRequestTimeouts();
    }

    private void verifyRealPendingConfirmationSurvivesRequestTimeouts() throws Exception {
        BrokerCluster stored = cluster();
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(local);
        when(local.getId()).thenReturn(new BrokerId("a"));
        when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
        when(adapter.getClusterSnapshot()).thenAnswer(call -> stored.snapshot());
        java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("permit-real-timeout-worker").factory(),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> {
            occupied.countDown();
            try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            BrokerMembershipAppService service = new BrokerMembershipAppService(adapter,
                    mock(com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter.class), new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                    new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
            ConfirmMembershipPermitDrainedParams fact = params(stored, "inc");
            AcceptMembershipPermitDrainedCommand command = acceptedFact(fact, "removed");
            java.util.concurrent.Future<MembershipConfirmationResult> original = service.submitExecutionConfirmation(command);
            MembershipPermitDrainedHandler handler = new MembershipPermitDrainedHandler(service, 1, 1);
            ClusterRevision revision = stored.revision();
            for (int request = 0; request < 8; request++) {
                assertThat(((com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult) handler.handleRequest(fact, invocation(fact, true).principal())).status())
                        .isEqualTo(com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult.Status.UNKNOWN);
                assertThat(service.submitExecutionConfirmation(command)).isSameAs(original);
                assertThat(original.isDone()).isFalse();
                assertThat(original.isCancelled()).isFalse();
            }
            assertThat(stored.revision()).isEqualTo(revision);
            verify(adapter, never()).changeMembershipConfiguration(any());
            release.countDown();
            assertThat(original.get(5, TimeUnit.SECONDS).status()).isEqualTo(MembershipConfirmationResult.Status.NOT_LEADER);
            MembershipPermitDrainedHandler retry = new MembershipPermitDrainedHandler(service, 1, 1000);
            assertThat(((com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult) retry.handleRequest(fact, invocation(fact, true).principal())).status())
                    .isEqualTo(com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult.Status.NOT_LEADER);
            verify(adapter, never()).changeMembershipConfiguration(any());
        } finally { release.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"sync", "async", "wrapped", "cancel"})
    void remoteConfirmationFailurePreservesCauseAndReleasesBudget(String mode) throws Exception {
        BrokerMembershipAppService service = mock(BrokerMembershipAppService.class);
        ConfirmMembershipPermitDrainedParams fact = params(cluster(), "inc");
        RuntimeException cause = new SecurityException("controlled confirmation failure");
        java.util.concurrent.CompletionException wrapped = new java.util.concurrent.CompletionException(cause);
        CompletableFuture<MembershipConfirmationResult> failed = new CompletableFuture<>();
        if (mode.equals("cancel")) { failed.cancel(false); }
        else { failed.completeExceptionally(mode.equals("wrapped") ? wrapped : cause); }
        when(service.submitExecutionConfirmation(acceptedFact(fact, "removed"))).thenAnswer(call -> {
            if (mode.equals("sync")) { throw cause; }
            return failed;
        }).thenReturn(CompletableFuture.completedFuture(confirmationCommitted()));
        MembershipPermitDrainedHandler handler = new MembershipPermitDrainedHandler(service, 1, 1000);
        if (mode.equals("sync")) {
            assertThatThrownBy(() -> handler.handleRequest(fact, invocation(fact, true).principal())).isSameAs(cause);
        } else if (mode.equals("cancel")) {
            assertThatThrownBy(() -> handler.handleRequest(fact, invocation(fact, true).principal()))
                    .isInstanceOf(java.util.concurrent.CancellationException.class);
        } else {
            assertThatThrownBy(() -> handler.handleRequest(fact, invocation(fact, true).principal()))
                    .isInstanceOf(java.util.concurrent.CompletionException.class).hasCause(cause);
        }
        assertThat(handler.handleRequest(fact, invocation(fact, true).principal())).isEqualTo(com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipPermitDrainedResponseFrom(confirmationCommitted()));
        verify(service, times(2)).submitExecutionConfirmation(acceptedFact(fact, "removed"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void interruptedRemoteWaitRetainsFiniteDeadlineAndRestoresInterrupt(boolean timeout) throws Exception {
        BrokerMembershipAppService service = mock(BrokerMembershipAppService.class);
        ConfirmMembershipPermitDrainedParams fact = params(cluster(), "inc");
        AtomicInteger waits = new AtomicInteger();
        java.util.List<Long> remaining = new java.util.ArrayList<>();
        CompletableFuture<MembershipConfirmationResult> pending = new CompletableFuture<>() {
            @Override
            public MembershipConfirmationResult get(long budget, TimeUnit unit) throws InterruptedException, TimeoutException {
                assertThat(unit).isEqualTo(TimeUnit.NANOSECONDS);
                remaining.add(budget);
                int attempt = waits.incrementAndGet();
                if (Thread.interrupted() || attempt < 4) { throw new InterruptedException("controlled repeated interrupt"); }
                if (timeout) { throw new TimeoutException("controlled deadline"); }
                return confirmationCommitted();
            }
        };
        when(service.submitExecutionConfirmation(acceptedFact(fact, "removed"))).thenReturn(pending)
                .thenReturn(CompletableFuture.completedFuture(confirmationCommitted()));
        MembershipPermitDrainedHandler handler = new MembershipPermitDrainedHandler(service, 1, 1000);
        try {
            Thread.currentThread().interrupt();
            com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult result = (com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult) handler.handleRequest(fact, invocation(fact, true).principal());
            assertThat(result.status()).isEqualTo(timeout ? com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult.Status.UNKNOWN
                    : com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult.Status.COMMITTED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(waits).hasValue(4);
            assertThat(remaining.getFirst()).isPositive().isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(1000));
            for (int index = 1; index < remaining.size(); index++) {
                assertThat(remaining.get(index)).isPositive().isLessThanOrEqualTo(remaining.get(index - 1));
            }
            assertThat(pending).isNotDone().isNotCancelled();
        } finally { Thread.interrupted(); }
        assertThat(handler.handleRequest(fact, invocation(fact, true).principal())).isEqualTo(com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipPermitDrainedResponseFrom(confirmationCommitted()));
        verify(service, times(2)).submitExecutionConfirmation(acceptedFact(fact, "removed"));
    }

    private static MembershipConfirmationResult confirmationCommitted() {
        return new MembershipConfirmationResult(MembershipConfirmationResult.Status.COMMITTED, null, "confirmed");
    }

    @Test
    void exactSelfRemovedMtlsExecutorCanConfirmWithoutGeneralControlAuthority() throws Exception {
        BrokerCluster cluster = cluster();
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        when(adapter.getClusterSnapshot()).thenReturn(cluster);
        when(adapter.getCommittedState()).thenReturn(
                com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer.INSTANCE.brokerClusterStateFrom(cluster));
        BrokerPeerAdmissionInterceptor admission = new BrokerPeerAdmissionInterceptor("a", adapter, "cluster-a",
                mock(BrokerGossipSettings.class));
        BrokerRequestInvocation<?> invocation = invocation(params(cluster, "inc"), true);
        assertThat(admission.intercept(invocation, ignored -> "accepted")).isEqualTo("accepted");
        assertThatThrownBy(() -> admission.intercept(new BrokerRequestInvocation<>(
                BrokerBoltOperation.FETCH_QUORUM_READ_INDEX,
                new com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams(), invocation.context(), invocation.principal()),
                ignored -> "accepted")).isInstanceOf(SecurityException.class);
    }

    @Test
    void leaderValidatesSameTermThenConsensusCasAndDuplicateConfirmationDoesNotRepeatNative() throws Exception {
        BrokerCluster stored = cluster();
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(local);
        when(local.getId()).thenReturn(new BrokerId("a"));
        when(local.isLeader()).thenReturn(true);
        when(local.getLeaderTerm()).thenReturn(java.util.Optional.of(new LeaderTerm(7L)));
        when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
        when(adapter.isMembershipPermitDrainCurrent(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(adapter.fetchQuorumReadIndex()).thenAnswer(ignored -> {
            assertThat(Thread.currentThread().getName()).isEqualTo("permit-confirm-worker");
            return java.util.concurrent.CompletableFuture.completedFuture(10L);
        });
        when(adapter.confirmMembershipPermitDrain(org.mockito.ArgumentMatchers.any())).thenAnswer(ignored -> {
            assertThat(Thread.currentThread().getName()).isEqualTo("permit-confirm-worker");
            java.util.concurrent.CompletableFuture<com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain> read =
                    new java.util.concurrent.CompletableFuture<>();
            Thread.ofPlatform().name("controlled-read-index-callback").start(() -> read.complete(
                    new com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain(new LeaderTerm(7L), 1L, new QuorumReadIndex(10L))));
            return read;
        });
        // 应用读取的是分离聚合，只有共识边界允许更新 stored。
        when(adapter.getClusterSnapshot()).thenAnswer(ignored -> stored.snapshot());
        when(adapter.confirmMembershipPermitDrained(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).isEqualTo("permit-confirm-worker");
            com.co.kc.imchat.broker.model.cqrs.command.ConfirmMembershipPermitDrainedCommand command = invocation.getArgument(0);
            BrokerClusterDecision decision = stored.confirmMembershipExecution(new MembershipExecutionConfirmation(
                    command.operationId(), command.stepId(), command.permitId(), command.executorBrokerId(),
                    command.executorIncarnation(), command.targetDigest(), command.result()));
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult(
                            decision.duplicate() ? com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status.DUPLICATE
                                    : com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status.APPLIED,
                            decision.operation(), null, null));
        });
        java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("permit-confirm-worker").factory(), new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
        BrokerMembershipAppService service = new BrokerMembershipAppService(adapter,
                mock(com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter.class), new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
        ConfirmMembershipPermitDrainedParams fact = params(stored, "inc");
        ClusterRevision before = stored.revision();
        assertThat(service.confirmExecution(acceptedFact(fact, "removed")).toCompletableFuture().join().status())
                .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED);
        assertThat(stored.revision()).isEqualTo(before.next());
        assertThat(service.confirmExecution(acceptedFact(fact, "removed")).toCompletableFuture().join().status())
                .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.DUPLICATE);
        assertThat(stored.revision()).isEqualTo(before.next());
        verify(adapter, never()).changeMembershipConfiguration(org.mockito.ArgumentMatchers.any());
        when(local.getLeaderTerm()).thenReturn(java.util.Optional.of(new LeaderTerm(8L)));
        assertThat(service.confirmExecution(acceptedFact(fact, "removed")).toCompletableFuture().join().status())
                .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.NOT_LEADER);
        assertThat(stored.revision()).isEqualTo(before.next());
        verify(adapter, times(2)).confirmMembershipPermitDrained(org.mockito.ArgumentMatchers.any());
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void successfulReadCallbackThenSameTermRoleRestartBeforeWorkerConsumptionMustNotCas() throws Exception {
        BrokerCluster stored = cluster();
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(local);
        when(local.getId()).thenReturn(new BrokerId("a"));
        when(local.isLeader()).thenReturn(true);
        when(local.getLeaderTerm()).thenReturn(java.util.Optional.of(new LeaderTerm(7L)));
        when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
        when(adapter.isMembershipPermitDrainCurrent(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(adapter.fetchQuorumReadIndex()).thenAnswer(ignored -> {
            assertThat(Thread.currentThread().getName()).isEqualTo("permit-confirm-worker");
            return java.util.concurrent.CompletableFuture.completedFuture(10L);
        });
        java.util.concurrent.CompletableFuture<com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain> read =
                new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        when(adapter.confirmMembershipPermitDrain(org.mockito.ArgumentMatchers.any())).thenAnswer(ignored -> {
            started.countDown(); return read;
        });
        // 应用读取的是分离聚合，只有共识边界允许更新 stored。
        when(adapter.getClusterSnapshot()).thenAnswer(ignored -> stored.snapshot());
        when(adapter.confirmMembershipPermitDrained(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).isEqualTo("permit-confirm-worker");
            com.co.kc.imchat.broker.model.cqrs.command.ConfirmMembershipPermitDrainedCommand command = invocation.getArgument(0);
            BrokerClusterDecision decision = stored.confirmMembershipExecution(new MembershipExecutionConfirmation(
                    command.operationId(), command.stepId(), command.permitId(), command.executorBrokerId(),
                    command.executorIncarnation(), command.targetDigest(), command.result()));
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult(
                            decision.duplicate() ? com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status.DUPLICATE
                                    : com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status.APPLIED,
                            decision.operation(), null, null));
        });
        java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("permit-confirm-worker").factory(), new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
        BrokerMembershipAppService service = new BrokerMembershipAppService(adapter,
                mock(com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter.class), new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
        ConfirmMembershipPermitDrainedParams fact = params(stored, "inc");
        ClusterRevision before = stored.revision();
        java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> response =
                service.confirmExecution(acceptedFact(fact, "removed"));
        assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        java.util.concurrent.CountDownLatch blocked = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        worker.execute(() -> {
            blocked.countDown();
            try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        });
        assertThat(blocked.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        read.complete(new com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain(
                new LeaderTerm(7L), 1L, new QuorumReadIndex(10L)));
        // callback 已成功；worker 尚未消费时角色 stop/start，term 相同、generation 不同。
        when(adapter.isMembershipPermitDrainCurrent(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        release.countDown();
        assertThat(response.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                .isNotEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED);
        assertThat(stored.revision()).isEqualTo(before);
        verify(adapter, never()).confirmMembershipPermitDrained(org.mockito.ArgumentMatchers.any());
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void concurrentIdenticalConfirmationSharesFlowWhileCancellationAndContradictionStayIsolated() throws Exception {
        BrokerCluster stored = cluster();
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(local);
        when(local.getId()).thenReturn(new BrokerId("a"));
        when(local.isLeader()).thenReturn(true);
        when(local.getLeaderTerm()).thenReturn(java.util.Optional.of(new LeaderTerm(7L)));
        when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
        when(adapter.isMembershipPermitDrainCurrent(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(adapter.fetchQuorumReadIndex()).thenAnswer(ignored -> {
            assertThat(Thread.currentThread().getName()).isEqualTo("permit-confirm-worker");
            return java.util.concurrent.CompletableFuture.completedFuture(10L);
        });
        java.util.concurrent.CompletableFuture<com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain> delayedRead =
                new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CountDownLatch readStarted = new java.util.concurrent.CountDownLatch(1);
        when(adapter.confirmMembershipPermitDrain(org.mockito.ArgumentMatchers.any())).thenAnswer(ignored -> {
            readStarted.countDown(); return delayedRead;
        });
        // 应用读取的是分离聚合，只有共识边界允许更新 stored。
        when(adapter.getClusterSnapshot()).thenAnswer(ignored -> stored.snapshot());
        when(adapter.confirmMembershipPermitDrained(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).isEqualTo("permit-confirm-worker");
            com.co.kc.imchat.broker.model.cqrs.command.ConfirmMembershipPermitDrainedCommand command = invocation.getArgument(0);
            BrokerClusterDecision decision = stored.confirmMembershipExecution(new MembershipExecutionConfirmation(
                    command.operationId(), command.stepId(), command.permitId(), command.executorBrokerId(),
                    command.executorIncarnation(), command.targetDigest(), command.result()));
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult(
                            decision.duplicate() ? com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status.DUPLICATE
                                    : com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status.APPLIED,
                            decision.operation(), null, null));
        });
        java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("permit-confirm-worker").factory(), new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
        BrokerMembershipAppService service = new BrokerMembershipAppService(adapter,
                mock(com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter.class), new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
        ConfirmMembershipPermitDrainedParams fact = params(stored, "inc");
        ClusterRevision before = stored.revision();
        java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> first =
                service.confirmExecution(acceptedFact(fact, "removed"));
        assertThat(readStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        java.util.concurrent.CompletableFuture<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> cancelled =
                service.confirmExecution(acceptedFact(fact, "removed")).toCompletableFuture();
        assertThat(cancelled.cancel(false)).isTrue();
        java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> duplicate =
                service.confirmExecution(acceptedFact(fact, "removed"));
        ConfirmMembershipPermitDrainedParams contradiction = new ConfirmMembershipPermitDrainedParams(
                fact.clusterId(), fact.callerBrokerId(), fact.receiverBrokerId(), fact.operationId(), fact.stepId(),
                fact.permitId(), fact.executorBrokerId(), fact.executorIncarnation(), fact.targetDigest(),
                ConfirmMembershipPermitDrainedParams.Result.TARGET_NOT_APPLIED);
        java.util.concurrent.CompletionStage<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> conflicting =
                service.confirmExecution(acceptedFact(contradiction, "removed"));
        delayedRead.complete(new com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain(new LeaderTerm(7L), 1L, new QuorumReadIndex(10L)));
        assertThat(first.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED);
        assertThat(duplicate.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED);
        assertThat(conflicting.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).status())
                .isIn(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.UNKNOWN,
                        com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.REJECTED);
        assertThat(cancelled).isCancelled();
        assertThat(stored.revision()).isEqualTo(before.next());
        verify(adapter, times(1)).confirmMembershipPermitDrain(org.mockito.ArgumentMatchers.any());
        verify(adapter, times(1)).confirmMembershipPermitDrained(org.mockito.ArgumentMatchers.any());
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void developmentAndForgedIncarnationCannotConfirm() {
        BrokerCluster cluster = cluster();
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        when(adapter.getClusterSnapshot()).thenReturn(cluster);
        when(adapter.getCommittedState()).thenReturn(
                com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer.INSTANCE.brokerClusterStateFrom(cluster));
        BrokerPeerAdmissionInterceptor admission = new BrokerPeerAdmissionInterceptor("a", adapter, "cluster-a",
                mock(BrokerGossipSettings.class));
        assertThatThrownBy(() -> admission.intercept(invocation(params(cluster, "inc"), false), ignored -> "accepted"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> admission.intercept(invocation(params(cluster, "forged"), true), ignored -> "accepted"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void remoteTimeoutReplayOnlyResendsTheSameTypedFact() {
        com.co.kc.imchat.plugin.bolt.spi.BoltInvoker invoker = mock(com.co.kc.imchat.plugin.bolt.spi.BoltInvoker.class);
        com.co.kc.imchat.plugin.bolt.client.BoltControlledClient transport = mock(
                com.co.kc.imchat.plugin.bolt.client.BoltControlledClient.class);
        when(transport.configuredMode()).thenReturn(com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode.MTLS);
        com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties properties =
                new com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties();
        com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter peer = new com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter(
                invoker, properties, Runnable::run, Runnable::run,
                new com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties(),
                new com.co.kc.imchat.broker.infrastructure.config.properties.BrokerFailureObservationProperties(),
                "removed", Runnable::run, transport);
        ConfirmMembershipPermitDrainedParams fact = params(cluster(), "inc");
        BrokerNode receiver = new BrokerNode(new BrokerId("a"), new BrokerRouteEndpoint("a:18020"),
                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE);
        com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult duplicate =
                new com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult(
                        com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult.Status.DUPLICATE, null, null);
        when(invoker.invoke(eq(new com.co.kc.imchat.plugin.bolt.model.BoltTarget("a:18020", "spiffe://im-chat/broker/a")),
                eq(BrokerBoltOperation.CONFIRM_MEMBERSHIP_PERMIT_DRAINED.service().service()),
                eq(BrokerBoltOperation.CONFIRM_MEMBERSHIP_PERMIT_DRAINED.operation()), same(fact),
                eq(com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult.class),
                eq(properties.getPeerCall().getTimeoutMs())))
                .thenThrow(new java.util.concurrent.CompletionException(new java.util.concurrent.TimeoutException("reply lost")))
                .thenReturn(duplicate);
        assertThatThrownBy(() -> peer.confirmMembershipPermitDrained(receiver, fact).toCompletableFuture().join())
                .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
        assertThat(peer.confirmMembershipPermitDrained(receiver, fact).toCompletableFuture().join()).isEqualTo(duplicate);
        verify(invoker, times(2)).invoke(any(), any(), any(), same(fact),
                eq(com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult.class), anyInt());
        verifyNoMoreInteractions(invoker);
    }

    @Test
    void admittedSelfRemovedFactDispatchesToTypedHandlerWithAuthenticatedId() throws Exception {
        BrokerCluster cluster = cluster();
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        when(adapter.getClusterSnapshot()).thenReturn(cluster);
        BrokerMembershipAppService service = mock(BrokerMembershipAppService.class);
        ConfirmMembershipPermitDrainedParams fact = params(cluster, "inc");
        com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult committed =
                new com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult(
                        com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED, null, null);
        when(service.submitExecutionConfirmation(acceptedFact(fact, "removed")))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(committed));
        com.co.kc.imchat.broker.interfaces.handler.broker.MembershipPermitDrainedHandler handler =
                new com.co.kc.imchat.broker.interfaces.handler.broker.MembershipPermitDrainedHandler(service, 1, 1000);
        BrokerPeerAdmissionInterceptor admission = new BrokerPeerAdmissionInterceptor("a", adapter, "cluster-a",
                mock(BrokerGossipSettings.class));
        assertThat(handler.requestType()).isEqualTo(ConfirmMembershipPermitDrainedParams.class);
        assertThat(handler.brokerOperation()).isEqualTo(BrokerBoltOperation.CONFIRM_MEMBERSHIP_PERMIT_DRAINED);
        assertThat(admission.intercept(invocation(fact, true), request ->
                handler.handleRequest(request.request(), request.principal()))).isEqualTo(
                        com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipPermitDrainedResponseFrom(committed));
        verify(service).submitExecutionConfirmation(acceptedFact(fact, "removed"));
        verifyNoMoreInteractions(service);
    }

    private static BrokerRequestInvocation<?> invocation(ConfirmMembershipPermitDrainedParams params, boolean tls) {
        String uri = "spiffe://im-chat/broker/removed";
        return new BrokerRequestInvocation<>(BrokerBoltOperation.CONFIRM_MEMBERSHIP_PERMIT_DRAINED, params,
                new BoltRequestContext("remote:100", "remote", tls ? uri : null, tls, tls ? Set.of(uri) : Set.of(), !tls),
                new BrokerCallerPrincipal(tls ? uri : "trusted-network://remote",
                        tls ? BrokerCallerRole.BROKER : BrokerCallerRole.TRUSTED_NETWORK));
    }

    private static ConfirmMembershipPermitDrainedParams params(BrokerCluster cluster, String incarnation) {
        BrokerMembershipStep step = cluster.findOperation(new ClusterOperationId("op")).orElseThrow().currentMembershipStep();
        return new ConfirmMembershipPermitDrainedParams("cluster-a", "removed", "a", "op", step.stepId(), "p",
                "removed", incarnation, step.targetDigest(), ConfirmMembershipPermitDrainedParams.Result.TARGET_APPLIED);
    }

    private static BrokerCluster cluster() {
        BrokerCluster cluster = BrokerCluster.builder().revision(new ClusterRevision(0L)).epoch(new RoutingEpoch(0L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(new BrokerNode(new BrokerId("a"), new BrokerRouteEndpoint("a:18020"),
                        BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE),
                        BrokerNode.discovered(new BrokerId("b"), new BrokerRouteEndpoint("b:18020"))))
                .operations(List.of()).operationHistoryCapacity(new OperationHistoryCapacity(100))
                .membershipEndpoints(Map.of(new BrokerId("a"), new MembershipEndpoint("a:18120"))).build();
        assertThat(cluster.addLearner(LearnerJoinIntent.builder().operationId(new ClusterOperationId("op"))
                .commandDigest(new CommandDigest("digest")).expectedRevision(cluster.revision()).brokerId(new BrokerId("b"))
                .membershipEndpoint(new MembershipEndpoint("b:18120")).changedAt(Instant.EPOCH).build()).accepted()).isTrue();
        BrokerMembershipStep step = cluster.findOperation(new ClusterOperationId("op")).orElseThrow().currentMembershipStep();
        assertThat(cluster.registerMembershipPermit(new ClusterOperationId("op"), step.stepId(), "p", new BrokerId("removed"),
                "inc", cluster.revision()).accepted()).isTrue();
        return cluster;
    }
    @Test
    void payloadCannotSupplyTrustedIdentityAndMismatchDoesNotAccessAdapter() throws Exception {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("permit-invalid-input-worker").factory(),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            BrokerMembershipAppService service = new BrokerMembershipAppService(adapter,
                    mock(com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter.class), new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                    new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
            AcceptMembershipPermitDrainedCommand valid = acceptedFact(params(cluster(), "inc"), "removed");
            for (String identity : new String[]{"other"}) {
                AcceptMembershipPermitDrainedCommand command = new AcceptMembershipPermitDrainedCommand(
                        valid.clusterId(), valid.callerBrokerId(), valid.receiverBrokerId(), valid.operationId(),
                        valid.stepId(), valid.permitId(), valid.executorBrokerId(), valid.executorIncarnation(),
                        valid.targetDigest(), valid.result(), identity);
                assertThat(service.confirmExecution(command).toCompletableFuture().join().status())
                        .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.REJECTED);
                java.util.concurrent.Future<MembershipConfirmationResult> rejected = service.submitExecutionConfirmation(command);
                assertThat(rejected.isDone()).isTrue();
                assertThat(rejected.cancel(true)).isFalse();
                assertThat(rejected.get().status()).isEqualTo(MembershipConfirmationResult.Status.REJECTED);
            }
            assertThatThrownBy(() -> new ConfirmMembershipPermitDrainedParams(valid.clusterId(), valid.callerBrokerId(),
                    valid.receiverBrokerId(), valid.operationId(), valid.stepId(), valid.permitId(), valid.executorBrokerId(),
                    valid.executorIncarnation(), valid.targetDigest(), null)).isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(adapter);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CLUSTER", "RECEIVER"})
    void wrongConfirmationDestinationDoesNotReadFollowingState(String mismatch) {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerMembershipExecutor executor = mock(BrokerMembershipExecutor.class);
        BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getClusterId()).thenReturn(new BrokerClusterId("local-cluster"));
        if (mismatch.equals("RECEIVER")) {
            when(adapter.getLocalBroker()).thenReturn(local);
            when(local.getId()).thenReturn(new BrokerId("local-broker"));
        }
        BrokerMembershipAppService service = new BrokerMembershipAppService(adapter,
                mock(com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter.class), new MinimumVoterCount(1), executor,
                new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
        AcceptMembershipPermitDrainedCommand command = new AcceptMembershipPermitDrainedCommand(
                mismatch.equals("CLUSTER") ? "other-cluster" : "local-cluster", "executor", "other-broker",
                "operation", "step", "permit", "executor", "incarnation", "digest",
                AcceptMembershipPermitDrainedCommand.Result.TARGET_APPLIED, "executor");
        assertThat(service.confirmExecution(command).toCompletableFuture().join())
                .isEqualTo(MembershipConfirmationResult.identityMismatch());
        verify(adapter, never()).getClusterSnapshot();
        if (mismatch.equals("CLUSTER")) {
            verify(adapter, never()).getLocalBroker();
        }
        verifyNoInteractions(executor);
    }

    private static AcceptMembershipPermitDrainedCommand acceptedFact(
            com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams request,
            String authenticatedExecutorBrokerId) {
        return new AcceptMembershipPermitDrainedCommand(request.clusterId(), request.callerBrokerId(),
                request.receiverBrokerId(), request.operationId(), request.stepId(), request.permitId(),
                request.executorBrokerId(), request.executorIncarnation(), request.targetDigest(),
                AcceptMembershipPermitDrainedCommand.Result.valueOf(request.result().name()), authenticatedExecutorBrokerId);
    }

}
