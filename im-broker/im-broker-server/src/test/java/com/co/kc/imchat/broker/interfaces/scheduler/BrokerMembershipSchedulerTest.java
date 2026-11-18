package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.domain.cluster.service.MembershipOperationService;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryMembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryLearnerConfirmationAttemptRepository;

import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;

import com.co.kc.imchat.broker.domain.cluster.model.LearnerJoinIntent;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.model.cqrs.command.AddLearnerCommand;
import com.co.kc.imchat.broker.support.StatefulClusterAdapterFixture;
import com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

class BrokerMembershipSchedulerTest {

    @Test
    void newLeaderCompletesPersistedPendingMembershipThroughApplicationService() {
        BrokerClusterState initialState = new BrokerClusterState(0L, 0L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-a", "10.0.0.1:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE),
                        new BrokerNode("broker-b", "10.0.0.2:18020", BrokerMembershipRole.DISCOVERED, BrokerRoutingState.INACTIVE)));
        StatefulClusterAdapterFixture original = fixtureWithVoterBinding(initialState);
        ClusterOperationId operationId = new ClusterOperationId("join");
        BrokerId brokerId = new BrokerId("broker-b");
        MembershipEndpoint endpoint = new MembershipEndpoint("10.0.0.2:18120");
        AddLearnerCommand command = new AddLearnerCommand(operationId.value(), brokerId.value(), 0L, endpoint.value());
        BrokerCluster cluster = original.adapter.getClusterSnapshot();
        BrokerClusterDecision pending = cluster.addLearner(LearnerJoinIntent.builder()
                .operationId(operationId).commandDigest(command.digest())
                .expectedRevision(cluster.revision()).brokerId(brokerId)
                .membershipEndpoint(endpoint).changedAt(Instant.EPOCH).build());
        assertThat(original.adapter.beginOperation(pending.operation()).toCompletableFuture().join().accepted()).isTrue();

        // 恢复持久化操作和端点绑定；模拟 Raft 成员已加入、Broker 拓扑尚未提交的状态。
        StatefulClusterAdapterFixture recovered = fixtureWithVoterBinding(initialState);
        recovered.stateMachine.restore(BrokerClusterSnapshot.decode(
                BrokerClusterSnapshot.encode(original.stateMachine.snapshotState())));
        recovered.learners.add(endpoint.value());
        java.util.concurrent.ThreadPoolExecutor worker = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("t5-scheduler-worker").factory(),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor executor =
                new com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor(new BrokerId("broker-a"), worker);
        executor.onRoleChanged(7L, 1L);
        java.util.concurrent.CompletableFuture<Void> completed = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer mapper =
                com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE;
        BrokerRaftAdapter.LocalBroker local = recovered.adapter.getLocalBroker();
        org.mockito.Mockito.doReturn(new BrokerId("broker-a")).when(local).getId();
        org.mockito.Mockito.doReturn(java.util.Optional.of(new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L)))
                .when(local).getLeaderTerm();
        org.mockito.Mockito.doReturn(new com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId("cluster-a"))
                .when(recovered.adapter).getClusterId();
        org.mockito.Mockito.doAnswer(call -> CompletableFuture.completedFuture(recovered.stateMachine.apply(call.getArgument(0))))
                .when(recovered.adapter).registerMembershipPermit(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doAnswer(call -> {
            assertThat(Thread.currentThread().getName()).isEqualTo("t5-scheduler-worker");
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult(
                    com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult.Status.APPLIED, 0, null, null));
        }).when(recovered.adapter).changeMembershipConfiguration(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doReturn(true).when(recovered.adapter).isMembershipPermitDrainCurrent(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doReturn(CompletableFuture.completedFuture(
                new com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipPermitDrain(
                        new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(7L), 1L,
                        new com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex(10L))))
                .when(recovered.adapter).confirmMembershipPermitDrain(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doReturn(CompletableFuture.completedFuture(10L)).when(recovered.adapter).fetchQuorumReadIndex();
        org.mockito.Mockito.doAnswer(call -> CompletableFuture.completedFuture(recovered.stateMachine.apply(call.getArgument(0))))
                .when(recovered.adapter).confirmMembershipPermitDrained(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doAnswer(call -> {
            com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation operation = call.getArgument(0);
            assertThat(operation.membershipReadyToCommit()).isTrue();
            assertThat(call.<com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision>getArgument(1))
                    .isEqualTo(recovered.stateMachine.getCluster().revision());
            return CompletableFuture.completedFuture(recovered.stateMachine.apply(mapper.commitCommandFrom(
                    operation, call.getArgument(1), Instant.EPOCH)));
        }).when(recovered.adapter).commitOperation(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doAnswer(call -> {
            com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult result =
                    recovered.stateMachine.apply(mapper.membershipCompletionCommandFrom(call.getArgument(0), Instant.EPOCH));
            completed.complete(null);
            return CompletableFuture.completedFuture(result);
        }).when(recovered.adapter).completeMembershipOperation(org.mockito.ArgumentMatchers.any());
        try (executor) {
            BrokerMembershipAppService service = new BrokerMembershipAppService(recovered.adapter,
                    recovered.peerAdapter, new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                    new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
            BrokerMembershipScheduler lifecycle = new BrokerMembershipScheduler(recovered.adapter, service, Duration.ofSeconds(4));
            when(recovered.adapter.getLocalBroker().isLeader()).thenReturn(false);
            lifecycle.reconcile();
            assertThat(calls.get()).isZero();
            when(recovered.adapter.getLocalBroker().isLeader()).thenReturn(true);
            lifecycle.reconcile();
            completed.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
            assertThat(recovered.stateMachine.findOperation(operationId).orElseThrow().phase())
                    .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
            assertThat(recovered.stateMachine.getCluster().membershipEndpoints()).containsEntry(brokerId, endpoint);
            assertThat(recovered.stateMachine.getCluster().findNode(brokerId).orElseThrow().membershipRole().name())
                    .isEqualTo("LEARNER_SYNCING");
            assertThat(calls.get()).isEqualTo(1);
            verify(recovered.adapter, org.mockito.Mockito.times(1)).changeMembershipConfiguration(org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void recoversOnlyAgedPendingOperationOnCurrentLeader() {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerMembershipAppService service = mock(BrokerMembershipAppService.class);
        BrokerCluster cluster = BrokerClusterStateTransformer.INSTANCE.brokerClusterFrom(
                new BrokerClusterState(0L, 0L,
                        BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                        List.of(new BrokerNode("broker-a", "10.0.0.1:18020",
                                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE),
                                new BrokerNode("broker-b", "10.0.0.2:18020",
                                        BrokerMembershipRole.DISCOVERED, BrokerRoutingState.INACTIVE))),
                List.of(), null, 10, java.util.Map.of("broker-a", "10.0.0.1:18120"));
        ClusterOperationId operationId = new ClusterOperationId("join");
        BrokerClusterDecision decision = cluster.addLearner(LearnerJoinIntent.builder()
                .operationId(operationId)
                .commandDigest(new CommandDigest("digest"))
                .expectedRevision(cluster.revision())
                .brokerId(new BrokerId("broker-b"))
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .changedAt(Instant.ofEpochMilli(1_000L))
                .build());
        assertThat(decision.accepted()).isTrue();
        when(adapter.getClusterSnapshot()).thenReturn(cluster);
        when(service.resume(operationId)).thenReturn(CompletableFuture.completedFuture(null));
        BrokerRaftAdapter.LocalBroker localBroker = BrokerRaftTestBindings.bindLocalBroker(adapter);
        when(localBroker.isLeader()).thenReturn(false);
        BrokerMembershipScheduler lifecycle = new BrokerMembershipScheduler(adapter, service, Duration.ofSeconds(4));

        lifecycle.reconcile();
        verify(service, never()).resume(operationId);

        when(localBroker.isLeader()).thenReturn(true);
        lifecycle.reconcile();
        verify(service).resume(operationId);
    }
    @Test
    void idleLeaderRequestsPeriodicDriftDiagnosticWithoutRecoveryOrRepair() {
        StatefulClusterAdapterFixture fixture = fixtureWithVoterBinding(new BrokerClusterState(0L, 0L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-a", "10.0.0.1:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE))));
        BrokerMembershipAppService service = mock(BrokerMembershipAppService.class);
        BrokerMembershipScheduler scheduler = new BrokerMembershipScheduler(fixture.adapter, service, Duration.ZERO);
        scheduler.reconcile();
        scheduler.reconcile();
        verify(service, times(2)).diagnoseMembershipConfiguration();
        verify(service, never()).resume(org.mockito.ArgumentMatchers.any());
        verify(fixture.adapter, never()).changeMembershipConfiguration(org.mockito.ArgumentMatchers.any());
    }

    private StatefulClusterAdapterFixture fixtureWithVoterBinding(BrokerClusterState state) {
        StatefulClusterAdapterFixture fixture = new StatefulClusterAdapterFixture(state);
        BrokerClusterSnapshot snapshot = fixture.stateMachine.snapshotState();
        fixture.stateMachine.restore(new BrokerClusterSnapshot(snapshot.formatVersion(), snapshot.clusterId(),
                snapshot.state(), snapshot.operations(), snapshot.fencingOperation(),
                java.util.Map.of("broker-a", "10.0.0.1:18120")));
        return fixture;
    }

}
