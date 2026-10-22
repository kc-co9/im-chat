package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;

import com.co.kc.imchat.broker.support.health.BrokerHealthObservationContext;
import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.cluster.model.*;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerFailureRecoveryScheduler;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerOperationReconcileCommand;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerProbeQuery;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerHealthReportQuery;
import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import com.co.kc.imchat.broker.support.TestClock;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrokerAutomaticFailureObservationTest {
    private final AtomicLong nanos = new AtomicLong();
    private MockedStatic<Instant> wallClock;
    private MockedStatic<MonotonicTimestamp> monotonicClock;

    @BeforeEach
    void controlTime() {
        wallClock = TestClock.interceptNow(() -> 100_000L);
        monotonicClock = mockStatic(MonotonicTimestamp.class, CALLS_REAL_METHODS);
        monotonicClock.when(MonotonicTimestamp::now).thenAnswer(ignored -> new MonotonicTimestamp(nanos.get()));
    }

    @AfterEach
    void releaseTimeScopes() {
        monotonicClock.close();
        wallClock.close();
    }

    @Test
    void lateProbeAndReportAreAcceptedButWrongContextCannotChangeTopology() {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker localBroker = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(localBroker);
        when(localBroker.getId()).thenReturn(new BrokerId("a"));
        BrokerCluster cluster = cluster();
        when(adapter.getClusterSnapshot()).thenReturn(cluster);
        when(adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120"), new MembershipEndpoint("c:18120")), Set.of()));
        BrokerHealthObservationTracker store = new BrokerHealthObservationTracker(new BrokerId("a"), Duration.ofSeconds(10));
        when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
        BrokerClusterAppService service = new BrokerClusterAppService(adapter, null, null,
                Duration.ofMillis(1), Duration.ofMillis(1), null, Duration.ofSeconds(1), store,
                policy(), Duration.ofSeconds(60));
        BrokerProbeQuery lateProbe = new BrokerProbeQuery("cluster-a", "b", "a", 0L, 0L, "late-probe");
        BrokerHealthReportQuery lateReport = new BrokerHealthReportQuery("cluster-a", "b", "a", 0L, 0L,
                "late-report", List.of("a", "b", "c"));
        BrokerHealthObservationContext context = new BrokerHealthObservationContext(
                cluster.revision(), cluster.topology(adapter.getConsensusMembership()));
        store.synchronize(context);
        store.record(context, new BrokerId("b"), BrokerHealthStatus.UNHEALTHY, new MonotonicTimestamp(0L));
        nanos.set(Duration.ofSeconds(1).toNanos());
        Instant receivedAt = Instant.ofEpochSecond(100_000);
        wallClock.when(Instant::now).thenReturn(receivedAt);
        assertThat(service.probeBroker(lateProbe).nonce()).isEqualTo("late-probe");
        assertThat(service.getHealthReport(lateReport)).satisfies(report -> {
            assertThat(report.observerBrokerId()).isEqualTo("a");
            assertThat(report.nonce()).isEqualTo("late-report");
            assertThat(report.revision()).isEqualTo(0L);
            assertThat(report.epoch()).isEqualTo(0L);
            assertThat(report.observations()).hasSize(3).filteredOn(row -> row.brokerId().equals("b"))
                    .singleElement().satisfies(row -> {
                        assertThat(row.status()).isEqualTo(BrokerHealthStatus.UNHEALTHY);
                        assertThat(row.observationAge()).isEqualTo(Duration.ofSeconds(1));
                        assertThat(row.unreachableDuration()).isEqualTo(Duration.ZERO);
                    });
        });
        assertThatThrownBy(() -> service.probeBroker(new BrokerProbeQuery("wrong", "b", "a", 0L, 0L,
                "nonce"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.probeBroker(new BrokerProbeQuery("cluster-a", "b", "a", 0L, 0L,
                "nonce")).brokerId()).isEqualTo("a");
        verify(adapter, never()).beginOperation(any());
    }

    @ParameterizedTest
    @CsvSource({
            "wrong,b,a,0,0,observation context mismatch",
            "cluster-a,b,b,0,0,observation context mismatch",
            "cluster-a,b,a,1,0,observation context mismatch",
            "cluster-a,b,a,0,1,observation context mismatch",
            "cluster-a,unknown,a,0,0,observation caller is not a current voter"
    })
    void observationEntrypointsCompareDomainIdentityAndVersion(String clusterId, String callerId,
            String targetId, Long revision, Long epoch, String message) {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker localBroker = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(localBroker);
        when(localBroker.getId()).thenReturn(new BrokerId("a"));
        when(adapter.getClusterSnapshot()).thenReturn(cluster());
        when(adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(
                new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120"),
                new MembershipEndpoint("c:18120")), Set.of()));
        when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
        BrokerClusterAppService service = new BrokerClusterAppService(adapter, null, null,
                Duration.ofMillis(1), Duration.ofMillis(1), null, Duration.ofSeconds(1),
                new BrokerHealthObservationTracker(new BrokerId("a"), Duration.ofSeconds(10)),
                policy(), Duration.ofSeconds(60));

        assertThatThrownBy(() -> service.probeBroker(new BrokerProbeQuery(clusterId, callerId, targetId,
                revision, epoch, "nonce")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
        assertThatThrownBy(() -> service.getHealthReport(new BrokerHealthReportQuery(clusterId, callerId, targetId,
                revision, epoch, "nonce", List.of("a", "b", "c"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
        verify(adapter, never()).beginOperation(any());
    }

    @Test
    void earlyRecoveryObtainsCurrentTermLeadershipInsteadOfWaitingForNewEpoch() {
        BrokerCluster cluster = cluster();
        BrokerClusterOperation operation = cluster.removeFailedBroker(BrokerFailureRemovalIntent.builder()
                .operationId(new ClusterOperationId("early-failure"))
                .commandDigest(new CommandDigest("digest"))
                .expectedRevision(cluster.revision()).brokerId(new BrokerId("b"))
                .progress(new DrainProgress(List.of(), Instant.EPOCH, null)).changedAt(Instant.EPOCH).build()).operation();
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker localBroker = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(localBroker);
        when(localBroker.getId()).thenReturn(new BrokerId("a"));
        when(adapter.findOperation(operation.id())).thenReturn(Optional.of(operation));
        when(adapter.confirmLeadership(any())).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("new leader quorum unavailable")));
        BrokerClusterAppService service = new BrokerClusterAppService(adapter, null, null,
                Duration.ofMillis(1), Duration.ofMillis(1), null, Duration.ofSeconds(1),
                new BrokerHealthObservationTracker(new BrokerId("a"), Duration.ofSeconds(10)), policy(),
                Duration.ofSeconds(60));

        assertThatThrownBy(() -> service.recoverRoutes(new BrokerOperationReconcileCommand(
                operation.id().value())).toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class).hasMessageContaining("new leader quorum unavailable");
        verify(adapter).confirmLeadership(any());
    }

    @Test
    void aProbeStillRunningKeepsTargetGateAcrossContextReset() {
        BrokerHealthObservationTracker store = new BrokerHealthObservationTracker(new BrokerId("a"), Duration.ofSeconds(10));
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker localBroker = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(localBroker);
        when(localBroker.getId()).thenReturn(new BrokerId("a"));
        when(adapter.getClusterSnapshot()).thenReturn(cluster());
        when(adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120"), new MembershipEndpoint("c:18120")), Set.of()));
        BrokerPeerAdapter peer = mock(BrokerPeerAdapter.class);
        CompletableFuture<BrokerHealthStatus> pending = new CompletableFuture<>();
        when(peer.probe(any(), any(), any(), any())).thenReturn(pending);
        when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
        BrokerClusterAppService service = new BrokerClusterAppService(adapter, peer, null,
                Duration.ofMillis(1), Duration.ofMillis(1), null, Duration.ofSeconds(1), store,
                policy(), Duration.ofSeconds(60));
        service.probeFailures();
        nanos.set(Duration.ofSeconds(20).toNanos());
        service.probeFailures();
        verify(peer, times(2)).probe(any(), any(), any(), any());
        assertThat(store.snapshot(new BrokerHealthObservationContext(new ClusterRevision(999L),
                        new BrokerClusterTopology(new RoutingEpoch(999L), List.of(), Map.of(),
                                new ConsensusMembership(Set.of(), Set.of()))),
                Instant.EPOCH, new MonotonicTimestamp(nanos.get()))).isEmpty();
        pending.complete(BrokerHealthStatus.UNHEALTHY);
        service.probeFailures();
        verify(peer, times(4)).probe(any(), any(), any(), any());
    }

    @Test
    void schedulerAndObservationShareRecoveryUntilUnderlyingQuorumActuallyCompletes() {
        BrokerCluster cluster = cluster();
        BrokerClusterOperation operation = cluster.removeFailedBroker(BrokerFailureRemovalIntent.builder()
                .operationId(new ClusterOperationId("shared-recovery"))
                .commandDigest(new CommandDigest("digest"))
                .expectedRevision(cluster.revision()).brokerId(new BrokerId("b"))
                .progress(new DrainProgress(List.of(), Instant.EPOCH, null)).changedAt(Instant.EPOCH).build()).operation();
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftTestBindings.bindLocalBroker(adapter);
        when(adapter.getLocalBroker().getId()).thenReturn(new BrokerId("a"));        when(adapter.getLocalBroker().isLeader()).thenReturn(true);
        when(adapter.getClusterSnapshot()).thenReturn(cluster);
        when(adapter.findOperation(operation.id())).thenReturn(Optional.of(operation));
        CompletableFuture<QuorumRead> quorum = new CompletableFuture<>();
        when(adapter.confirmLeadership(any())).thenReturn(quorum);
        when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
        BrokerClusterAppService service = new BrokerClusterAppService(adapter, null, null,
                Duration.ofMillis(1), Duration.ofMillis(1), null, Duration.ofSeconds(1),
                new BrokerHealthObservationTracker(new BrokerId("a"), Duration.ofSeconds(10)),
                policy(), Duration.ofSeconds(60));
        BrokerFailureRecoveryScheduler scheduler =
                new BrokerFailureRecoveryScheduler(adapter, service);

        scheduler.reconcile();
        service.reconcileFailures();
        scheduler.reconcile();
        verify(adapter, times(1)).confirmLeadership(any());

        quorum.completeExceptionally(new IllegalStateException("controlled quorum loss"));
        CompletableFuture<QuorumRead> retry = new CompletableFuture<>();
        when(adapter.confirmLeadership(any())).thenReturn(retry);
        service.reconcileFailures();
        scheduler.reconcile();
        verify(adapter, times(2)).confirmLeadership(any());
        retry.completeExceptionally(new IllegalStateException("controlled second quorum loss"));
        verify(adapter.getLocalBroker(), never()).getLeaderTerm();
    }

    private static BrokerFailureObservationService policy() {
        return new BrokerFailureObservationService(Duration.ofSeconds(30), Duration.ofSeconds(10));
    }

    static BrokerCluster cluster() {
        return BrokerCluster.builder().revision(new ClusterRevision(0L)).epoch(new RoutingEpoch(0L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(node("a"), node("b"), node("c"))).operations(List.of())
                .operationHistoryCapacity(new OperationHistoryCapacity(100))
                .membershipEndpoints(Map.of(new BrokerId("a"), new MembershipEndpoint("a:18120"),
                        new BrokerId("b"), new MembershipEndpoint("b:18120"),
                        new BrokerId("c"), new MembershipEndpoint("c:18120"))).build();
    }

    private static BrokerNode node(String id) {
        return new BrokerNode(new BrokerId(id), new BrokerRouteEndpoint(id + ":18020"), BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE);
    }
}
