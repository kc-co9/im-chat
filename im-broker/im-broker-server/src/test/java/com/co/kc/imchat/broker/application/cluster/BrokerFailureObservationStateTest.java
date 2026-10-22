package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;

import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.OperationHistoryCapacity;
import com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import com.co.kc.imchat.broker.support.TestClock;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 通过用例的报告请求边界观察冷却，不访问私有状态。 */
class BrokerFailureObservationStateTest {
    private final ConsensusMembership membership = new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120"), new MembershipEndpoint("c:18120")), Set.of());
    private final BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
    private final BrokerPeerAdapter peer = mock(BrokerPeerAdapter.class);
    private final AtomicLong nanos = new AtomicLong();
    private final AtomicLong wallMillis = new AtomicLong(100_000L);
    private final AtomicLong term = new AtomicLong(1L);
    private BrokerCluster cluster = BrokerAutomaticFailureObservationTest.cluster();
    private final BrokerClusterAppService app;

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

    BrokerFailureObservationStateTest() {
        when(BrokerRaftTestBindings.bindLocalBroker(adapter).isLeader()).thenReturn(true);
        when(adapter.getLocalBroker().getId()).thenReturn(new BrokerId("a"));
        when(adapter.getLocalBroker().getLeaderTerm()).thenAnswer(ignored -> Optional.of(new LeaderTerm(term.get())));
        when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
        when(adapter.getClusterSnapshot()).thenAnswer(ignored -> cluster);
        when(adapter.getConsensusMembership()).thenReturn(membership);
        when(adapter.confirmLeadership(any())).thenAnswer(ignored -> CompletableFuture.completedFuture(
                new QuorumRead(new LeaderTerm(term.get()), new QuorumReadIndex(1L))));
        when(peer.fetchHealthReport(any(), any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        app = new BrokerClusterAppService(adapter, peer, null, Duration.ofMillis(1), Duration.ofMillis(1), null,
                Duration.ofSeconds(10),
                new BrokerHealthObservationTracker(new BrokerId("a"), Duration.ofSeconds(10)),
                new BrokerFailureObservationService(Duration.ofSeconds(30), Duration.ofSeconds(10)),
                Duration.ofSeconds(60));
    }

    @Test
    void missingLocalTermSkipsWithoutQuorumAndInvalidatesCooldown() {
        tick(0L, false);
        when(adapter.getLocalBroker().getLeaderTerm()).thenReturn(Optional.empty());
        tick(seconds(60), false);
        tick(seconds(120), false);
        verify(adapter, never()).confirmLeadership(any());
        when(adapter.getLocalBroker().getLeaderTerm()).thenAnswer(ignored -> Optional.of(new LeaderTerm(term.get())));
        tick(seconds(121), false);
        tick(seconds(181) - 1L, false);
        verify(adapter, never()).confirmLeadership(any());
        tick(seconds(181), true);
        verify(adapter).confirmLeadership(any());
    }

    @Test
    void revisionChangeDuringLocalTermReadInvalidatesBeforeMembershipAndRecools() {
        tick(0L, false);
        when(adapter.getLocalBroker().getLeaderTerm()).thenAnswer(ignored -> {
            changeContext(1L, 0L);
            return Optional.of(new LeaderTerm(term.get()));
        });
        clearInvocations(adapter);
        tick(seconds(60), false);
        verify(adapter, never()).getConsensusMembership();
        verify(adapter, never()).confirmLeadership(any());
        when(adapter.getLocalBroker().getLeaderTerm()).thenAnswer(ignored -> Optional.of(new LeaderTerm(term.get())));
        tick(seconds(61), false);
        tick(seconds(121) - 1L, false);
        tick(seconds(121), true);
    }

    @Test
    void startupWaitsFullCooldownAndRevisionOnlyChangesDoNotRestartIt() {
        tick(0L, false);
        changeContext(1L, 0L);
        tick(seconds(60) - 1L, false);
        changeContext(2L, 0L);
        tick(seconds(60), true);
        tick(seconds(60) + 1L, true);
    }

    @Test
    void wallJumpsDoNotSpendOrRestartMonotonicCooldown() {
        tick(0L, false);
        wallMillis.set(1_000_000L);
        tick(seconds(60) - 1L, false);
        wallMillis.set(1L);
        tick(seconds(60), true);
    }

    @Test
    void reorderedMembershipDoesNotRestartCooldown() {
        MembershipEndpoint a = new MembershipEndpoint("Aa:18120");
        MembershipEndpoint b = new MembershipEndpoint("BB:18120");
        MembershipEndpoint c = new MembershipEndpoint("c:18120");
        cluster = BrokerCluster.builder().revision(cluster.revision()).epoch(cluster.epoch())
                .algorithm(cluster.algorithm()).brokers(cluster.brokers()).operations(List.of())
                .operationHistoryCapacity(new OperationHistoryCapacity(100))
                .membershipEndpoints(java.util.Map.of(new BrokerId("a"), a, new BrokerId("b"), b, new BrokerId("c"), c)).build();
        when(adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(
                new java.util.LinkedHashSet<>(List.of(a, b, c)), Set.of()));
        tick(0L, false);
        when(adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(
                new java.util.LinkedHashSet<>(List.of(c, b, a)), Set.of()));
        tick(seconds(60), true);
        tick(seconds(61), true);
    }

    @Test
    void leaderTermAndRoleChangesRestartFullCooldown() {
        tick(0L, false);
        tick(seconds(60), true);
        term.set(2L);
        tick(seconds(61), false);
        tick(seconds(121) - 1L, false);
        tick(seconds(121), true);
        when(adapter.getLocalBroker().isLeader()).thenReturn(false);
        clearInvocations(adapter);
        tick(seconds(122), false);
        verify(adapter, never()).getConsensusMembership();
        when(adapter.getLocalBroker().isLeader()).thenReturn(true);
        tick(seconds(123), false);
        tick(seconds(183) - 1L, false);
        tick(seconds(183), true);
    }

    @Test
    void membershipMismatchCannotSpendCooldownUntilConsistencyReturns() {
        tick(0L, false);
        when(adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120")), Set.of(new MembershipEndpoint("c:18120"))));
        tick(seconds(70), false);
        tick(seconds(140), false);
        when(adapter.getConsensusMembership()).thenReturn(membership);
        tick(seconds(200), false);
        tick(seconds(260) - 1L, false);
        tick(seconds(260), true);
        tick(seconds(260) + 1L, true);
    }

    @Test
    void unknownRaftEndpointAndMonotonicRegressionFailClosed() {
        when(adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120"), new MembershipEndpoint("unknown:18120")), Set.of()));
        tick(0L, false);
        tick(seconds(60), false);
        when(adapter.getConsensusMembership()).thenReturn(membership);
        tick(seconds(20), false);
        tick(seconds(80), true);
        tick(0L, false);
        tick(seconds(60) - 1L, false);
        tick(seconds(60), true);
    }

    @Test
    void routingTopologyChangeRestartsCooldownWithoutChangingVoters() {
        tick(0L, false);
        tick(seconds(60), true);
        changeContext(1L, 1L);
        tick(seconds(61), false);
        tick(seconds(121) - 1L, false);
        tick(seconds(121), true);
    }

    private void tick(long at, boolean reportsExpected) {
        nanos.set(at);
        clearInvocations(peer);
        app.reconcileFailures().toCompletableFuture().join();
        verify(peer, times(reportsExpected ? 2 : 0)).fetchHealthReport(any(), any(), any(), any(), any());
        verify(adapter, never()).beginOperation(any());
        assertThat(cluster.operations()).isEmpty();
    }

    private void changeContext(long revision, long epoch) {
        cluster = BrokerCluster.builder().revision(new ClusterRevision(revision)).epoch(new RoutingEpoch(epoch))
                .algorithm(cluster.algorithm()).brokers(cluster.brokers()).membershipEndpoints(cluster.membershipEndpoints())
                .operations(List.of()).operationHistoryCapacity(new OperationHistoryCapacity(100)).build();
    }

    private long seconds(long value) {
        return Duration.ofSeconds(value).toNanos();
    }
}
