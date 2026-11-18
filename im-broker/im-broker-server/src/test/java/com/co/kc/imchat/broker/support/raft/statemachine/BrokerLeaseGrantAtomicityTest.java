package com.co.kc.imchat.broker.support.raft.statemachine;

import java.util.Map;
import com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantRequest;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantResult;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerActivationIntent;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.model.cqrs.command.CommitRoutingEpochFenceCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class BrokerLeaseGrantAtomicityTest {
    @Test
    void fenceAppliedBeforeGrantRejectsWithoutAllocatingSequence() {
        RaftBrokerClusterStateMachine machine = machine();
        RoutingLeaseGrantService grants = spy(new RoutingLeaseGrantService(Duration.ofSeconds(1)));
        assertThat(machine.apply(new CommitRoutingEpochFenceCommand(fence(machine))).accepted()).isTrue();

        LeaseGrantResult result = machine.grantLease(request(), proof(), grants);

        assertThat(result.status()).isEqualTo(LeaseGrantResult.Status.FENCED);
        verify(grants, never()).grant(any(), any());
    }

    @Test
    void fenceCannotApplyBetweenAdmissionAndSequenceAllocation() throws Exception {
        RaftBrokerClusterStateMachine machine = machine();
        RoutingEpochFence fence = fence(machine);
        RoutingLeaseGrantService grants = spy(new RoutingLeaseGrantService(Duration.ofSeconds(1)));
        CountDownLatch allocationEntered = new CountDownLatch(1);
        CountDownLatch allowAllocation = new CountDownLatch(1);
        doAnswer(invocation -> {
            allocationEntered.countDown();
            assertThat(allowAllocation.await(5, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(grants).grant(any(), any());
        CompletableFuture<BrokerClusterConsensusResult> fenceApplied = new CompletableFuture<>();
        Thread fenceThread = new Thread(() -> {
            try {
                fenceApplied.complete(machine.apply(new CommitRoutingEpochFenceCommand(fence)));
            } catch (Throwable failure) {
                fenceApplied.completeExceptionally(failure);
            }
        }, "fence-atomicity-test");

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CompletableFuture<LeaseGrantResult> granted = CompletableFuture.supplyAsync(
                    () -> machine.grantLease(request(), proof(), grants), executor);
            try {
                assertThat(allocationEntered.await(5, TimeUnit.SECONDS)).isTrue();
                fenceThread.start();
                // 签发资格已经通过，但序列尚未分配；此时 fence 必须阻塞在同一状态机锁上。
                await().atMost(Duration.ofSeconds(5)).until(() -> fenceThread.getState() == Thread.State.BLOCKED);
                assertThat(fenceApplied).isNotDone();
                allowAllocation.countDown();
                assertThat(granted.get(5, TimeUnit.SECONDS).status()).isEqualTo(LeaseGrantResult.Status.GRANTED);
                assertThat(fenceApplied.get(5, TimeUnit.SECONDS).accepted()).isTrue();
                assertThat(machine.grantLease(request(), proof(), grants).status()).isEqualTo(LeaseGrantResult.Status.FENCED);
            } finally {
                allowAllocation.countDown();
                fenceThread.join(5_000L);
            }
        }
    }

    private RaftBrokerClusterStateMachine machine() {
        return new RaftBrokerClusterStateMachine("cluster-a", new BrokerClusterState(1L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(
                new BrokerNode("broker-a", "10.0.0.1:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE),
                new BrokerNode("broker-b", "10.0.0.2:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.READY))), 1000, Map.of());
    }

    private RoutingEpochFence fence(RaftBrokerClusterStateMachine machine) {
        BrokerClusterOperation operation = machine.getCluster().activate(BrokerActivationIntent.builder()
                .operationId(new ClusterOperationId("activate-b")).commandDigest(new CommandDigest("activate-b"))
                .expectedRevision(machine.getCluster().revision()).brokerId(new BrokerId("broker-b"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build()).operation();
        assertThat(machine.apply(BrokerClusterRaftTransformer.INSTANCE.beginCommandFrom(operation)).accepted()).isTrue();
        return RoutingEpochFence.builder().operationId(operation.id()).epoch(new RoutingEpoch(7L))
                .leaderTerm(term(3L)).quorumReadIndex(readIndex(42L))
                .fencedBrokerIds(List.of(new BrokerId("broker-a"))).build();
    }

    private LeaseGrantRequest request() {
        return new LeaseGrantRequest(new BrokerId("broker-a"), new RoutingEpoch(7L), new LeaseNonce("nonce-1"));
    }

    private QuorumRead proof() {
        return new QuorumRead(term(3L), readIndex(42L));
    }
}
