package com.co.kc.imchat.broker.interfaces.scheduler;

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
        StatefulClusterAdapterFixture original = new StatefulClusterAdapterFixture(initialState);
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
        StatefulClusterAdapterFixture recovered = new StatefulClusterAdapterFixture(initialState);
        recovered.stateMachine.restore(BrokerClusterSnapshot.decode(
                BrokerClusterSnapshot.encode(original.stateMachine.snapshotState())));
        recovered.learners.add(endpoint.value());
        BrokerMembershipAppService service = new BrokerMembershipAppService(recovered.adapter,
                mock(com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter.class),
                new MinimumVoterCount(1));
        BrokerMembershipScheduler lifecycle = new BrokerMembershipScheduler(recovered.adapter, service, Duration.ofSeconds(4));

        org.mockito.Mockito.when(recovered.adapter.getLocalBroker().isLeader()).thenReturn(false);
        lifecycle.reconcile();
        verify(recovered.adapter, never()).addLearner(endpoint);
        org.mockito.Mockito.when(recovered.adapter.getLocalBroker().isLeader()).thenReturn(true);
        lifecycle.reconcile();
        lifecycle.reconcile();

        assertThat(recovered.stateMachine.findOperation(operationId).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
        assertThat(recovered.stateMachine.getCluster().membershipEndpoints()).containsEntry(brokerId, endpoint);
        assertThat(recovered.stateMachine.getCluster().findNode(brokerId).orElseThrow().membershipRole().name())
                .isEqualTo("LEARNER_SYNCING");
        verify(recovered.adapter, times(1)).addLearner(endpoint);
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
                List.of(), null, 10);
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
}
