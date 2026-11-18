package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.authority.model.FenceAck;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantRequest;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateRoutingLeaseCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.support.BrokerDomainValues;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerClusterLeaseGrantTest {
    private InMemoryFenceStore currentFences;
    private BrokerPeerAdapter peerAdapter;
    private final AtomicReference<RoutingLease> installedLease = new AtomicReference<>();

    @Test
    void unrelatedRevisionChangeDuringFenceReadDoesNotPreventSubmission() {
        AtomicReference<BrokerClusterState> state = new AtomicReference<>(state(7L, active("broker-a")));
        StubConsensus consensus = new StubConsensus(state);
        InMemoryFenceStore fences = new InMemoryFenceStore();
        CompletableFuture<QuorumRead> pendingRead = new CompletableFuture<>();
        consensus.nextRead = pendingRead;
        CompletableFuture<RoutingEpochFence> closing = closeThroughActivation(service(adapter(consensus, fences)), "close-7", 7L).toCompletableFuture();
        assertThat(closing).isNotDone();

        // 无关进度更新改变 revision，但 epoch 和 ACTIVE 集合保持不变。
        state.set(new BrokerClusterState(8L, 7L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(active("broker-a"))));
        pendingRead.complete(new QuorumRead(term(3L), readIndex(50L)));

        assertThat(closing.join().epoch()).isEqualTo(new RoutingEpoch(7L));
        assertThat(fences.findRoutingFence()).contains(closing.join());
    }
    private BrokerClusterAppService service(BrokerRaftAdapter adapter) {
        return new BrokerClusterAppService(adapter, peerAdapter,
                mock(GatewayRegistry.class),
                Duration.ofMillis(1), Duration.ofSeconds(1),
                mock(ScheduledExecutorService.class), Duration.ofSeconds(10), null, null, Duration.ofSeconds(60));
    }


    @Test
    void fenceCommittedWhileReadIsPendingPreventsOldEpochGrant() {
        AtomicReference<BrokerClusterState> state = new AtomicReference<>(state(7L, active("broker-a")));
        StubConsensus consensus = new StubConsensus(state);
        InMemoryFenceStore fenceStore = new InMemoryFenceStore();
        CompletableFuture<QuorumRead> pendingRead = new CompletableFuture<>();
        consensus.nextRead = pendingRead;
        BrokerClusterAppService coordinator =
                service(adapter(consensus, fenceStore));

        CompletableFuture<Boolean> pendingGrant = coordinator.activateLease(
                request("broker-a", 7L, "pending")).toCompletableFuture();
        assertThat(pendingGrant).isNotDone();

        // 状态 revision/epoch 不变，仅 fence 在 ReadIndex 返回前提交。
        consensus.succeed(3L, 50L);
        closeThroughActivation(coordinator, "close-7", 7L)
                .toCompletableFuture().join();
        assertThat(state.get().epoch()).isEqualTo(7L);
        assertThat(state.get().revision()).isEqualTo(7L);
        pendingRead.complete(new QuorumRead(term(3L), readIndex(42L)));

        assertThat(pendingGrant.join()).isFalse();
        assertThat(installedLease.get()).isNull();

        // 拒绝旧申请不消耗签发序列，新 epoch 首次签发仍从 1 开始。
        state.set(state(8L, active("broker-a")));
        consensus.succeed(3L, 51L);
        assertThat(coordinator.activateLease(request("broker-a", 8L, "current"))
                .toCompletableFuture().join()).isTrue();
        assertThat(installedLease.get().leaseSequence().value()).isEqualTo(1L);
    }

    @Test
    void progressRevisionChangeDuringQuorumReadDoesNotPreventLeaseInstallation() {
        AtomicReference<BrokerClusterState> state = new AtomicReference<>(state(7L, active("broker-a")));
        StubConsensus consensus = new StubConsensus(state);
        CompletableFuture<QuorumRead> pendingRead = new CompletableFuture<>();
        consensus.nextRead = pendingRead;
        BrokerClusterAppService service = service(adapter(consensus, new InMemoryFenceStore()));

        CompletableFuture<Boolean> activation = service.activateLease(request("broker-a", 7L, "renew"))
                .toCompletableFuture();
        assertThat(activation).isNotDone();
        state.set(new BrokerClusterState(8L, 7L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(active("broker-a"))));
        pendingRead.complete(new QuorumRead(term(3L), readIndex(50L)));

        assertThat(activation.join()).isTrue();
        assertThat(installedLease.get().routingEpoch()).isEqualTo(new RoutingEpoch(7L));
        assertThat(installedLease.get().leaseSequence().value()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"epoch", "membership"})
    void lostAdmissionDuringQuorumReadPreventsLeaseInstallation(String change) {
        AtomicReference<BrokerClusterState> state = new AtomicReference<>(state(7L,
                active("broker-a"), active("broker-b")));
        StubConsensus consensus = new StubConsensus(state);
        CompletableFuture<QuorumRead> pendingRead = new CompletableFuture<>();
        consensus.nextRead = pendingRead;
        BrokerClusterAppService service = service(adapter(consensus, new InMemoryFenceStore()));

        CompletableFuture<Boolean> activation = service.activateLease(request("broker-a", 7L, "renew"))
                .toCompletableFuture();
        assertThat(activation).isNotDone();
        if (change.equals("epoch")) {
            state.set(state(8L, active("broker-a"), active("broker-b")));
        } else {
            state.set(new BrokerClusterState(8L, 7L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                    List.of(new BrokerNode("broker-a", "broker-a.example:19000",
                            BrokerMembershipRole.VOTER, BrokerRoutingState.READY), active("broker-b"))));
        }
        pendingRead.complete(new QuorumRead(term(3L), readIndex(50L)));

        assertThat(activation.join()).isFalse();
        assertThat(installedLease.get()).isNull();
    }

    @Test
    void grantsOnlyAfterCurrentTermQuorumReadAndUsesMonotonicSequence() {
        AtomicReference<BrokerClusterState> state = new AtomicReference<>(state(7L,
                active("broker-a"), active("broker-b")));
        StubConsensus consensus = new StubConsensus(state);
        InMemoryFenceStore fenceStore = new InMemoryFenceStore();
        BrokerClusterAppService coordinator =
                service(adapter(consensus, fenceStore));

        consensus.nextRead = CompletableFuture.failedFuture(
                new IllegalStateException("quorum unavailable"));
        assertThat(coordinator.activateLease(request("broker-a", 7L, "nonce-1"))
                .toCompletableFuture().join()).isFalse();
        assertThat(installedLease.get()).isNull();

        consensus.succeed(3L, 42L);
        assertThat(coordinator.activateLease(request(
                "broker-a", 7L, "nonce-2")).toCompletableFuture().join()).isTrue();
        RoutingLease first = installedLease.get();
        consensus.succeed(3L, 43L);
        assertThat(coordinator.activateLease(request(
                "broker-a", 7L, "nonce-3")).toCompletableFuture().join()).isTrue();
        RoutingLease second = installedLease.get();

        assertThat(first.leaderTerm()).isEqualTo(term(3L));
        assertThat(first.leaseSequence()).isEqualTo(
                BrokerDomainValues.leaseSequence(1L));
        assertThat(first.quorumReadIndex()).isEqualTo(readIndex(42L));
        assertThat(second.leaseSequence()).isEqualTo(
                BrokerDomainValues.leaseSequence(2L));

        consensus.succeed(2L, 44L);
        assertThat(coordinator.activateLease(request("broker-a", 7L, "nonce-4"))
                .toCompletableFuture().join()).isFalse();
        assertThat(installedLease.get()).isSameAs(second);
    }

    @Test
    void persistedFenceSurvivesCoordinatorRestartAndForbidsFutureEpochPregrant() {
        AtomicReference<BrokerClusterState> state = new AtomicReference<>(state(7L,
                active("broker-a"), active("broker-b")));
        StubConsensus consensus = new StubConsensus(state);
        InMemoryFenceStore fenceStore = new InMemoryFenceStore();
        consensus.succeed(3L, 50L);
        BrokerClusterAppService first =
                service(adapter(consensus, fenceStore));

        RoutingEpochFence operation = closeThroughActivation(first, "operation-1", 7L)
                .toCompletableFuture().join();
        assertThat(operation.fencedBrokerIds()).extracting(BrokerId::value)
                .containsExactly("broker-a", "broker-b");
        assertThat(fenceStore.findRoutingFence().orElseThrow()).isEqualTo(operation);

        BrokerClusterAppService restarted =
                service(adapter(consensus, fenceStore));
        consensus.succeed(4L, 51L);
        RoutingEpochFence resumed = closeThroughActivation(restarted, "operation-1", 7L)
                .toCompletableFuture().join();
        assertThat(resumed.leaderTerm()).isEqualTo(term(4L));
        assertThat(resumed.quorumReadIndex()).isEqualTo(readIndex(51L));
        assertThat(resumed.fencedBrokerIds()).extracting(BrokerId::value)
                .containsExactly("broker-a", "broker-b");
        assertThat(restarted.activateLease(request("broker-a", 7L, "old"))
                .toCompletableFuture().join()).isFalse();
        assertThat(restarted.activateLease(request("broker-a", 8L, "future"))
                .toCompletableFuture().join()).isFalse();
        assertThat(installedLease.get()).isNull();

        state.set(state(8L, active("broker-a")));
        consensus.succeed(4L, 52L);
        assertThat(restarted.activateLease(request("broker-a", 8L, "current"))
                .toCompletableFuture().join()).isTrue();
        assertThat(installedLease.get().routingEpoch()).isEqualTo(new RoutingEpoch(8L));
        assertThat(fenceStore.findRoutingFence().orElseThrow().fencedBrokerIds())
                .extracting(BrokerId::value)
                .containsExactly("broker-a", "broker-b");
    }

    private ActivateRoutingLeaseCommand request(String brokerId, long epoch, String operationId) {
        return new ActivateRoutingLeaseCommand(operationId, brokerId, epoch);
    }

    private BrokerClusterState state(long epoch, BrokerNode... brokers) {
        return new BrokerClusterState(epoch, epoch,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(brokers));
    }

    private BrokerNode active(String brokerId) {
        return new BrokerNode(brokerId, brokerId + ".example:19000",
                BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE);
    }

    private CompletionStage<RoutingEpochFence> closeThroughActivation(
            BrokerClusterAppService service, String operationId, long epoch) {
        return service.activate(new ActivateBrokerCommand(operationId, "broker-ready", epoch))
                .handle((result, failure) -> {
                    assertThat(failure).hasCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("failed to commit epoch topology: NOT_LEADER");
                    return currentFences.findRoutingFence().orElseThrow();
                });
    }

    private BrokerRaftAdapter adapter(StubConsensus consensus, InMemoryFenceStore fences) {
        currentFences = fences;
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        peerAdapter = mock(BrokerPeerAdapter.class);
        RoutingLeaseGrantService grantService = new RoutingLeaseGrantService(Duration.ofNanos(100));
        when(adapter.grantLease(any(), any())).thenAnswer(invocation -> {
            LeaseGrantRequest request = invocation.getArgument(0);
            return adapter.getClusterSnapshot().checkLeaseGrant(request.brokerId(), request.routingEpoch())
                    .orElseGet(() -> grantService.grant(request, invocation.getArgument(1)));
        });
        when(peerAdapter.prepareLease(any(), any(), any())).thenAnswer(invocation ->
                CompletableFuture.completedFuture(new LeaseNonce(
                        ((ClusterOperationId) invocation.getArgument(2)).value())));
        when(peerAdapter.installLease(any(), any())).thenAnswer(invocation -> {
            installedLease.set(invocation.getArgument(1));
            return CompletableFuture.completedFuture(true);
        });
        when(adapter.getClusterSnapshot()).thenAnswer(ignored -> {
            BrokerClusterState state = consensus.currentState();
            ArrayList<BrokerNode> nodes = new ArrayList<>(state.brokers());
            nodes.add(new BrokerNode("broker-ready", "ready:18020",
                    BrokerMembershipRole.LEARNER, BrokerRoutingState.READY));
            return BrokerClusterStateTransformer.INSTANCE.brokerClusterFrom(
                    new BrokerClusterState(state.revision(), state.epoch(), state.algorithm(), nodes),
                    List.of(), fences.findRoutingFence().orElse(null), 10);
        });
        when(adapter.beginOperation(any())).thenAnswer(invocation ->
                CompletableFuture.completedFuture(
                        BrokerClusterConsensusResult.applied(
                                invocation.getArgument(0))));
        when(adapter.commitOperation(any())).thenReturn(CompletableFuture.completedFuture(
                BrokerClusterConsensusResult.notLeader(
                        null, "stop after fence")));
        when(peerAdapter.fence(any(), any())).thenAnswer(invocation -> {
            BrokerId brokerId = ((com.co.kc.imchat.broker.domain.cluster.model.BrokerNode)
                    invocation.getArgument(0)).id();
            RoutingEpochFence fence = invocation.getArgument(1);
            return CompletableFuture.completedFuture(
                    FenceAck.builder()
                            .brokerId(brokerId).routingEpoch(fence.epoch()).leaderTerm(fence.leaderTerm())
                            .operationId(fence.operationId())
                            .fencedLeaseSequence(BrokerDomainValues.leaseSequence(1L))
                            .build());
        });
        when(adapter.getCommittedState()).thenAnswer(ignored -> consensus.currentState());
        when(adapter.confirmLeadership(any())).thenAnswer(invocation ->
                consensus.confirmLeadership(invocation.getArgument(0)));
        when(adapter.findRoutingFence()).thenAnswer(ignored -> fences.findRoutingFence());
        when(adapter.commitRoutingFence(any())).thenAnswer(invocation -> fences.persist(invocation.getArgument(0)));
        return adapter;
    }

    private static final class StubConsensus  {

        private final AtomicReference<BrokerClusterState> state;
        private CompletionStage<QuorumRead> nextRead;

        private StubConsensus(AtomicReference<BrokerClusterState> state) {
            this.state = state;
        }

        public BrokerClusterState currentState() {
            return state.get();
        }

        public CompletionStage<QuorumRead> confirmLeadership(QuorumReadNonce requestNonce) {
            return nextRead;
        }

        void succeed(long term, long readIndex) {
            nextRead = CompletableFuture.completedFuture(
                    new QuorumRead(term(term), readIndex(readIndex)));
        }
    }

    private static final class InMemoryFenceStore  {

        private RoutingEpochFence operation;

        public Optional<RoutingEpochFence> findRoutingFence() {
            return Optional.ofNullable(operation);
        }

        public CompletionStage<RoutingEpochFence> persist(RoutingEpochFence operation) {
            this.operation = operation;
            return CompletableFuture.completedFuture(operation);
        }
    }
}
