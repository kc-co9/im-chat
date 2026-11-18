package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.authority.model.FenceAck;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.result.ActivateBrokerResult;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.support.StatefulClusterAdapterFixture;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.LongFunction;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.leaseSequence;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class BrokerClusterAppServiceFenceTest {
    private StatefulClusterAdapterFixture cluster;
    private BrokerPeerAdapter peerAdapter;
    @Test
    void completesEarlyOnlyAfterEveryFrozenHolderReturnsBoundAck() {
        RoutingEpochFence operation = operation();
        StubGrantFence grantFence = new StubGrantFence(operation);
        AtomicInteger quarantineCalls = new AtomicInteger();
        BrokerClusterAppService service = service(grantFence,
                (brokerId, persistedOperation) -> CompletableFuture.completedFuture(
                        ack(brokerId, persistedOperation, persistedOperation.leaderTerm())),
                duration -> {
                    quarantineCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                });

        ActivateBrokerResult result = service.activate(new ActivateBrokerCommand(
                "operation-1", "broker-c", 7L)).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(cluster.stateMachine.committedState().epoch()).isEqualTo(8L);
        assertThat(quarantineCalls).hasValue(0);
    }

    @Test
    void missingOrInvalidAckRequiresFullQuarantine() {
        RoutingEpochFence operation = operation();
        CompletableFuture<Void> quarantine = new CompletableFuture<>();
        StubGrantFence grantFence = new StubGrantFence(operation);
        AtomicInteger quarantineCalls = new AtomicInteger();
        AtomicLong waitedNanos =
                new AtomicLong();
        BrokerClusterAppService service = service(grantFence,
                (brokerId, persistedOperation) -> {
                    if (brokerId.equals("broker-a")) {
                        return CompletableFuture.completedFuture(
                                ack(brokerId, persistedOperation,
                                        persistedOperation.leaderTerm()));
                    }
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("ack lost"));
                },
                duration -> {
                    quarantineCalls.incrementAndGet();
                    waitedNanos.set(duration);
                    return quarantine;
                });

        CompletableFuture<ActivateBrokerResult> activation = service.activate(new ActivateBrokerCommand(
                "operation-1", "broker-c", 7L)).toCompletableFuture();
        assertThat(activation).isNotDone();
        assertThat(cluster.stateMachine.committedState().epoch()).isEqualTo(7L);
        quarantine.complete(null);
        ActivateBrokerResult result = activation.join();

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(cluster.stateMachine.committedState().epoch()).isEqualTo(8L);
        assertThat(quarantineCalls).hasValue(1);
        assertThat(waitedNanos).hasValue(1_000L);
    }

    @Test
    void ackFromOldLeaderTermCannotShortenQuarantine() {
        RoutingEpochFence operation = operation();
        CompletableFuture<Void> quarantine = new CompletableFuture<>();
        BrokerClusterAppService service = service(new StubGrantFence(operation),
                (brokerId, persistedOperation) -> CompletableFuture.completedFuture(
                        ack(brokerId, persistedOperation,
                                term(persistedOperation.leaderTerm().value() - 1L))),
                duration -> {
                    assertThat(duration).isEqualTo(1_000L);
                    return quarantine;
                });

        CompletableFuture<ActivateBrokerResult> activation = service.activate(new ActivateBrokerCommand(
                "operation-1", "broker-c", 7L)).toCompletableFuture();
        assertThat(activation).isNotDone();
        assertThat(cluster.stateMachine.committedState().epoch()).isEqualTo(7L);
        quarantine.complete(null);
        ActivateBrokerResult result = activation.join();

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(cluster.stateMachine.committedState().epoch()).isEqualTo(8L);
    }

    @Test
    void rejectedPeerExecutionStillRequiresQuarantineBeforeCommit() {
        CompletableFuture<Void> quarantine = new CompletableFuture<>();
        AtomicInteger waited = new AtomicInteger();
        BrokerClusterAppService service = service(new StubGrantFence(operation()),
                (brokerId, fence) -> CompletableFuture.failedFuture(
                        new RejectedExecutionException("peer queue full")),
                duration -> {
                    waited.incrementAndGet();
                    assertThat(duration).isEqualTo(1_000L);
                    return quarantine;
                });
        CompletableFuture<ActivateBrokerResult> activation = service.activate(
                new ActivateBrokerCommand("operation-1", "broker-c", 7L)).toCompletableFuture();
        assertThat(activation).isNotDone();
        assertThat(waited).hasValue(1);
        assertThat(cluster.stateMachine.committedState().epoch()).isEqualTo(7L);
        quarantine.complete(null);
        assertThat(activation.join().status()).isEqualTo("APPLIED");
        assertThat(cluster.stateMachine.committedState().epoch()).isEqualTo(8L);
    }

    @Test
    void rejectedQuarantineSchedulingFailsActivationWithoutCommittingEpoch() {
        RejectedExecutionException failure = new RejectedExecutionException("scheduler stopped");
        BrokerClusterAppService service = service(new StubGrantFence(operation()),
                (brokerId, fence) -> CompletableFuture.failedFuture(new IllegalStateException("ack lost")),
                duration -> { throw failure; });

        CompletableFuture<ActivateBrokerResult> activation = service.activate(
                new ActivateBrokerCommand("operation-1", "broker-c", 7L)).toCompletableFuture();

        assertThatThrownBy(activation::join).hasCause(failure);
        assertThat(cluster.stateMachine.committedState().epoch()).isEqualTo(7L);
        assertThat(cluster.stateMachine.findOperation(new ClusterOperationId("operation-1")).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.FENCING_OLD_EPOCH);
    }

    @Test
    void rejectedFencePersistenceStopsPeerRequestsAndTopologyCommit() {
        BrokerClusterAppService service = service(new StubGrantFence(operation()),
                (brokerId, fence) -> { throw new AssertionError("must not dispatch fence RPC"); },
                duration -> { throw new AssertionError("must not schedule quarantine"); });
        IllegalStateException failure = new IllegalStateException("cannot persist conflicting fence");
        Mockito.doReturn(CompletableFuture.failedFuture(failure))
                .when(cluster.adapter).commitRoutingFence(any());

        CompletableFuture<ActivateBrokerResult> activation = service.activate(
                new ActivateBrokerCommand("operation-1", "broker-c", 7L)).toCompletableFuture();

        assertThatThrownBy(activation::join).hasCause(failure);
        Mockito.verify(peerAdapter, Mockito.never()).fence(any(), any());
        Mockito.verify(cluster.adapter, Mockito.never()).commitOperation(any());
        assertThat(cluster.stateMachine.committedState().epoch()).isEqualTo(7L);
    }

    @Test
    void rejectedTopologyCommitFailsWithoutAuthorizingNewEpoch() {
        BrokerClusterAppService service = service(new StubGrantFence(operation()),
                (brokerId, fence) -> CompletableFuture.completedFuture(ack(brokerId, fence, fence.leaderTerm())),
                duration -> { throw new AssertionError("must not schedule quarantine"); });
        Mockito.doReturn(CompletableFuture.completedFuture(
                BrokerClusterConsensusResult.rejected("topology changed")))
                .when(cluster.adapter).commitOperation(any());

        CompletableFuture<ActivateBrokerResult> activation = service.activate(
                new ActivateBrokerCommand("operation-1", "broker-c", 7L)).toCompletableFuture();

        assertThatThrownBy(activation::join).hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed to commit epoch topology: REJECTED: topology changed");
        Mockito.verify(service, Mockito.never()).activateLease(any());
        assertThat(cluster.stateMachine.committedState().epoch()).isEqualTo(7L);
    }

    private BrokerClusterAppService service(
            StubGrantFence grantFence,
            BiFunction<String, RoutingEpochFence, CompletionStage<FenceAck>> fenceRequests,
            LongFunction<CompletionStage<Void>> quarantineWaiter) {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            long duration = invocation.getArgument(1);
            quarantineWaiter.apply(duration).thenRun(task);
            return null;
        }).when(scheduler).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS));
        cluster = new StatefulClusterAdapterFixture(new BrokerClusterState(
                0L, 7L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(
                new BrokerNode("broker-a", "127.0.0.1:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE),
                new BrokerNode("broker-b", "127.0.0.1:18021", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE),
                new BrokerNode("broker-c", "127.0.0.1:18022", BrokerMembershipRole.LEARNER, BrokerRoutingState.READY))));
        peerAdapter = mock(BrokerPeerAdapter.class);
        doAnswer(invocation -> fenceRequests.apply(
                ((com.co.kc.imchat.broker.domain.cluster.model.BrokerNode) invocation.getArgument(0)).id().value(),
                invocation.getArgument(1))).when(peerAdapter).fence(any(), any());
        BrokerClusterAppService service = spy(new BrokerClusterAppService(cluster.adapter,
                peerAdapter,
                mock(GatewayRegistry.class), Duration.ofNanos(1_000L), Duration.ofSeconds(1), scheduler, Duration.ofSeconds(10), null, null, Duration.ofSeconds(60)));
        Mockito.doReturn(CompletableFuture.completedFuture(
                new QuorumRead(
                        grantFence.operation.leaderTerm(), grantFence.operation.quorumReadIndex())))
                .when(cluster.adapter).confirmLeadership(any(QuorumReadNonce.class));
        Mockito.doReturn(CompletableFuture.completedFuture(true))
                .when(service).activateLease(any());
        return service;
    }

    private RoutingEpochFence operation() {
        return RoutingEpochFence.builder()
                .operationId(new ClusterOperationId("operation-1"))
                .epoch(new RoutingEpoch(7L))
                .leaderTerm(term(3L))
                .quorumReadIndex(readIndex(42L))
                .fencedBrokerIds(List.of(new BrokerId("broker-a"), new BrokerId("broker-b")))
                .build();
    }

    private FenceAck ack(String brokerId, RoutingEpochFence operation,
                         LeaderTerm leaderTerm) {
        return FenceAck.builder()
                .brokerId(new BrokerId(brokerId))
                .routingEpoch(operation.epoch())
                .leaderTerm(leaderTerm)
                .fencedLeaseSequence(leaseSequence(9L))
                .operationId(operation.operationId())
                .build();
    }

    private static final class StubGrantFence  {

        private final RoutingEpochFence operation;

        private StubGrantFence(RoutingEpochFence operation) {
            this.operation = operation;
        }


    }
}
