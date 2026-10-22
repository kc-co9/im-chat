package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.domain.authority.model.FenceAck;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.LeaseSequence;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerOperationReconcileCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import com.co.kc.imchat.broker.support.StatefulClusterAdapterFixture;
import com.co.kc.imchat.broker.support.TestClock;
import com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BrokerAutomaticFailureFlowTest {
    private final AtomicLong nanos = new AtomicLong();
    private final AtomicLong wallMillis = new AtomicLong(100_000L);
    private MockedStatic<Instant> wallClock;
    private MockedStatic<MonotonicTimestamp> monotonicClock;

    @BeforeEach
    void controlTime() {
        wallClock = TestClock.interceptNow(wallMillis::get);
        monotonicClock = mockStatic(MonotonicTimestamp.class, CALLS_REAL_METHODS);
        monotonicClock.when(MonotonicTimestamp::now).thenAnswer(ignored -> new MonotonicTimestamp(nanos.get()));
    }

    @AfterEach
    void releaseTimeScopes() {
        monotonicClock.close();
        wallClock.close();
    }

    @Test
    void twoIndependentStableReportsDriveRealBeginFenceEpochLeaseAndRecovery() {
        BrokerClusterState initial = new BrokerClusterState(0L, 0L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(node("a"), node("b"), node("c")));
        StatefulClusterAdapterFixture fixture = new StatefulClusterAdapterFixture(initial);
        fixture.stateMachine.restore(new BrokerClusterSnapshot(2, "cluster-a", initial, List.of(), null,
                Map.of("a", "a:18120", "b", "b:18120", "c", "c:18120")));
        fixture.voters.addAll(List.of("a:18120", "b:18120", "c:18120"));
        when(BrokerRaftTestBindings.bindLocalBroker(fixture.adapter).isLeader()).thenReturn(true);
        when(fixture.adapter.getLocalBroker().getId()).thenReturn(new BrokerId("a"));
        when(fixture.adapter.getLocalBroker().getLeaderTerm()).thenReturn(Optional.of(new LeaderTerm(1L)));
        when(fixture.adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
        BrokerPeerAdapter peer = fixture.peerAdapter;
        BrokerHealthObservationTracker store = new BrokerHealthObservationTracker(new BrokerId("a"), Duration.ofSeconds(10));
        ScheduledExecutorService quarantine = mock(ScheduledExecutorService.class);
        doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; })
                .when(quarantine).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS));
        BrokerClusterAppService app = new BrokerClusterAppService(fixture.adapter, peer, mock(GatewayRegistry.class),
                Duration.ofMillis(1), Duration.ofNanos(1), quarantine, Duration.ofSeconds(10), store,
                new BrokerFailureObservationService(Duration.ofSeconds(30), Duration.ofSeconds(10)),
                Duration.ofSeconds(60));
        when(peer.probe(any(), any(), any(), any())).thenAnswer(call -> {
            com.co.kc.imchat.broker.domain.cluster.model.BrokerNode target = call.getArgument(0);
            return target.id().value().equals("b") ? CompletableFuture.completedFuture(BrokerHealthStatus.UNHEALTHY) : CompletableFuture.completedFuture(BrokerHealthStatus.HEALTHY);
        });
        when(peer.fetchHealthReport(any(), any(), any(), any(), any())).thenAnswer(call -> {
            com.co.kc.imchat.broker.domain.cluster.model.BrokerNode reporter = call.getArgument(0);
            return reporter.id().value().equals("c") ? CompletableFuture.completedFuture(List.of(
                    BrokerHealthObservation.builder().brokerId(new BrokerId("b")).observerBrokerId(new BrokerId("c"))
                            .status(BrokerHealthStatus.UNHEALTHY).observedAt(Instant.now())
                            .unreachableBeginAt(Instant.now().minusSeconds(30)).build()))
                    : CompletableFuture.failedFuture(new IllegalStateException("failed reporter cannot vote"));
        });
        CompletableFuture<FenceAck> pendingFence = new CompletableFuture<>();
        when(peer.fence(any(), any())).thenAnswer(call -> {
            com.co.kc.imchat.broker.domain.cluster.model.BrokerNode target = call.getArgument(0);
            RoutingEpochFence fence = call.getArgument(1);
            if (target.id().value().equals("b")) {
                return pendingFence;
            }
            return CompletableFuture.completedFuture(FenceAck.builder().brokerId(target.id()).routingEpoch(fence.epoch())
                    .leaderTerm(fence.leaderTerm()).fencedLeaseSequence(new LeaseSequence(0L)).operationId(fence.operationId()).build());
        });
        when(peer.prepareLease(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(new LeaseNonce("prepared")));
        when(peer.installLease(any(), any())).thenReturn(CompletableFuture.completedFuture(true));

        app.reconcileFailures().toCompletableFuture().join();
        for (long second = 0; second <= 60; second += 5) {
            nanos.set(Duration.ofSeconds(second).toNanos());
            app.probeFailures().toCompletableFuture().join();
        }
        CompletableFuture<Void> automatic = app.reconcileFailures().toCompletableFuture();
        BrokerClusterOperation accepted = fixture.stateMachine.getCluster().operations().getFirst();
        assertThat(app.recoverRoutes(new BrokerOperationReconcileCommand(accepted.id().value())).toCompletableFuture())
                .isDone();
        org.mockito.Mockito.verify(peer, org.mockito.Mockito.times(3)).fence(any(), any());
        RoutingEpochFence activeFence = fixture.stateMachine.getCluster().findRoutingFence().orElseThrow();
        pendingFence.complete(FenceAck.builder().brokerId(new BrokerId("b")).routingEpoch(activeFence.epoch())
                .leaderTerm(activeFence.leaderTerm()).fencedLeaseSequence(new LeaseSequence(0L))
                .operationId(activeFence.operationId()).build());
        automatic.join();

        assertThat(fixture.stateMachine.committedState().epoch()).isEqualTo(1L);
        assertThat(fixture.stateMachine.committedState().brokers()).filteredOn(n -> n.brokerId().equals("b"))
                .singleElement().satisfies(n -> assertThat(n.routingState()).isEqualTo(BrokerRoutingState.INACTIVE));
        BrokerClusterOperation operation = fixture.stateMachine.getCluster().operations().getFirst();
        assertThat(operation.id().value()).isEqualTo("auto-failure:cluster-a:b:0");
        assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.REBUILDING_ROUTES);
        /* 越过固定墙上时间下尚未到期的旧事件 TTL。 */
        wallMillis.incrementAndGet();
        app.recoverRoutes(new BrokerOperationReconcileCommand(operation.id().value())).toCompletableFuture().join();
        assertThat(fixture.stateMachine.findOperation(operation.id()).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
    }

    private BrokerNode node(String id) {
        return new BrokerNode(id, id + ":18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE);
    }
}
