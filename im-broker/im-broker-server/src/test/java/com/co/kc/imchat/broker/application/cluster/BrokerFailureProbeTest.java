package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;

import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.OperationHistoryCapacity;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthObservationDTO;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerHealthReportQuery;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.broker.support.TestClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrokerFailureProbeTest {
    private MockedStatic<Instant> wallClock;
    private MockedStatic<MonotonicTimestamp> monotonicClock;

    @AfterEach
    void releaseTimeScopes() {
        monotonicClock.close();
        wallClock.close();
    }

    @Test
    void targetsReleaseIndependentlyAfterCrossThreadCompletion() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<BrokerHealthStatus> targetB = new CompletableFuture<>();
        CompletableFuture<BrokerHealthStatus> targetC = new CompletableFuture<>();
        when(fixture.peer.probe(any(), any(), any(), any())).thenAnswer(call -> {
            com.co.kc.imchat.broker.domain.cluster.model.BrokerNode target = call.getArgument(0);
            return target.id().equals(new BrokerId("b")) ? targetB : targetC;
        });
        CompletableFuture<Void> round = fixture.app.probeFailures().toCompletableFuture();
        fixture.app.probeFailures().toCompletableFuture().join();
        verify(fixture.peer, times(2)).probe(any(), any(), any(), any());

        CompletableFuture.runAsync(() -> targetB.complete(BrokerHealthStatus.UNHEALTHY))
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(round).isNotDone();
        fixture.app.probeFailures().toCompletableFuture().join();
        verify(fixture.peer, times(3)).probe(any(), any(), any(), any());

        CompletableFuture.runAsync(() -> targetC.completeExceptionally(new IllegalStateException("transport failed")))
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
        round.get(5, java.util.concurrent.TimeUnit.SECONDS);
        fixture.app.probeFailures().toCompletableFuture().join();
        verify(fixture.peer, times(5)).probe(any(), any(), any(), any());
    }

    @Test
    void changedGenerationKeepsInFlightGateAndRejectsOldCompletionWrite() {
        Fixture fixture = new Fixture();
        CompletableFuture<BrokerHealthStatus> pending = new CompletableFuture<>();
        when(fixture.peer.probe(any(), any(), any(), any())).thenReturn(pending);
        fixture.app.probeFailures();
        BrokerCluster before = fixture.cluster;
        fixture.cluster = BrokerCluster.builder().revision(new ClusterRevision(1L)).epoch(before.epoch())
                .algorithm(before.algorithm()).brokers(before.brokers()).operations(List.of())
                .membershipEndpoints(before.membershipEndpoints()).operationHistoryCapacity(new OperationHistoryCapacity(100)).build();
        fixture.app.probeFailures();
        verify(fixture.peer, times(2)).probe(any(), any(), any(), any());
        pending.complete(BrokerHealthStatus.UNHEALTHY);
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNKNOWN);
        assertThat(fixture.row().unreachableDuration()).isZero();
        fixture.app.probeFailures();
        verify(fixture.peer, times(4)).probe(any(), any(), any(), any());
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNHEALTHY);
    }

    @Test
    void reorderedMembershipPreservesFailureSamplingAndChangedMembershipInvalidatesIt() {
        Fixture fixture = new Fixture();
        MembershipEndpoint a = new MembershipEndpoint("Aa:18120");
        MembershipEndpoint b = new MembershipEndpoint("BB:18120");
        MembershipEndpoint c = new MembershipEndpoint("c:18120");
        BrokerCluster initial = fixture.cluster;
        fixture.cluster = BrokerCluster.builder().revision(initial.revision()).epoch(initial.epoch())
                .algorithm(initial.algorithm()).brokers(initial.brokers()).operations(List.of())
                .membershipEndpoints(java.util.Map.of(new BrokerId("a"), a, new BrokerId("b"), b, new BrokerId("c"), c))
                .operationHistoryCapacity(new OperationHistoryCapacity(100)).build();
        when(fixture.adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(
                new java.util.LinkedHashSet<>(List.of(a, b, c)), Set.of()));
        when(fixture.peer.probe(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BrokerHealthStatus.UNHEALTHY));
        fixture.app.probeFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(5).toNanos());
        when(fixture.adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(
                new java.util.LinkedHashSet<>(List.of(c, b, a)), Set.of()));
        fixture.app.probeFailures().toCompletableFuture().join();
        assertThat(fixture.row().unreachableDuration()).isEqualTo(Duration.ofSeconds(5));
        when(fixture.adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(a, b), Set.of(c)));
        fixture.app.probeFailures().toCompletableFuture().join();
        when(fixture.adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(a, b, c), Set.of()));
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNKNOWN);
        assertThat(fixture.row().unreachableDuration()).isZero();
    }

    @Test
    void overlappingMembershipInitialAndAsyncReadsReleaseProbeGateWithoutInvalidSample() {
        Fixture fixture = new Fixture();
        ConsensusMembership valid = fixture.adapter.getConsensusMembership();
        when(fixture.adapter.getConsensusMembership()).thenAnswer(ignored -> new ConsensusMembership(
                Set.of(new MembershipEndpoint("a:18120")), Set.of(new MembershipEndpoint("a:18120"))));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.app.probeFailures())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("overlap");
        verifyNoInteractions(fixture.peer);
        doReturn(valid).when(fixture.adapter).getConsensusMembership();
        CompletableFuture<BrokerHealthStatus> pending = new CompletableFuture<>();
        when(fixture.peer.probe(any(), any(), any(), any())).thenReturn(pending);
        CompletableFuture<Void> probe = fixture.app.probeFailures().toCompletableFuture();
        when(fixture.adapter.getConsensusMembership()).thenAnswer(ignored -> new ConsensusMembership(
                Set.of(new MembershipEndpoint("a:18120")), Set.of(new MembershipEndpoint("a:18120"))));
        pending.complete(BrokerHealthStatus.UNHEALTHY);
        org.assertj.core.api.Assertions.assertThatThrownBy(probe::join)
                .hasCauseInstanceOf(IllegalStateException.class);
        doReturn(valid).when(fixture.adapter).getConsensusMembership();
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNKNOWN);
        when(fixture.peer.probe(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BrokerHealthStatus.UNHEALTHY));
        fixture.app.probeFailures().toCompletableFuture().join();
        verify(fixture.peer, times(4)).probe(any(), any(), any(), any());
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNHEALTHY);
        verify(fixture.adapter, never()).beginOperation(any());
    }

    @Test
    void localFailureIsUnknownAndSuccessAndUnknownResetFailureWindow() {
        Fixture fixture = new Fixture();
        when(fixture.peer.probe(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BrokerHealthStatus.UNHEALTHY));
        fixture.app.probeFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(5).toNanos());
        fixture.app.probeFailures().toCompletableFuture().join();
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNHEALTHY);
        assertThat(fixture.row().unreachableDuration()).isEqualTo(Duration.ofSeconds(5));
        when(fixture.peer.probe(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BrokerHealthStatus.HEALTHY));
        fixture.nanos.set(Duration.ofSeconds(6).toNanos());
        fixture.app.probeFailures().toCompletableFuture().join();
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.HEALTHY);
        assertThat(fixture.row().unreachableDuration()).isZero();
        when(fixture.peer.probe(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BrokerHealthStatus.UNKNOWN));
        fixture.nanos.set(Duration.ofSeconds(7).toNanos());
        fixture.app.probeFailures().toCompletableFuture().join();
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNKNOWN);
        when(fixture.peer.probe(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BrokerHealthStatus.UNHEALTHY));
        fixture.nanos.set(Duration.ofSeconds(8).toNanos());
        fixture.app.probeFailures().toCompletableFuture().join();
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNHEALTHY);
        assertThat(fixture.row().unreachableDuration()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "HEALTHY,-1,HEALTHY", "HEALTHY,0,HEALTHY", "HEALTHY,500000000,HEALTHY",
            "HEALTHY,500000001,HEALTHY", "UNHEALTHY,-1,UNHEALTHY", "UNHEALTHY,0,UNHEALTHY",
            "UNHEALTHY,500000001,UNHEALTHY", "UNKNOWN,-1,UNKNOWN", "UNKNOWN,0,UNKNOWN",
            "UNKNOWN,500000001,UNKNOWN"
    })
    void probeStatusDoesNotDependOnAppElapsedTime(BrokerHealthStatus status, long elapsed,
            BrokerHealthStatus expected) {
        Fixture fixture = new Fixture();
        fixture.nanos.set(1L);
        when(fixture.peer.probe(any(), any(), any(), any())).thenAnswer(call -> {
            fixture.nanos.set(1L + elapsed);
            return CompletableFuture.completedFuture(status);
        });
        fixture.app.probeFailures().toCompletableFuture().join();
        assertThat(fixture.row().status()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"HEALTHY", "UNHEALTHY"})
    void snapshotBeforeCompletedSampleRemainsUnknown(BrokerHealthStatus status) {
        Fixture fixture = new Fixture();
        fixture.nanos.set(1L);
        when(fixture.peer.probe(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(status));
        fixture.app.probeFailures().toCompletableFuture().join();
        assertThat(fixture.row().status()).isEqualTo(status);

        fixture.nanos.set(0L);
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNKNOWN);
        assertThat(fixture.row().unreachableDuration()).isZero();
    }

    @Test
    void exceptionalProbeCompletionIsUnknownAndReleasesGate() {
        Fixture fixture = new Fixture();
        when(fixture.peer.probe(any(), any(), any(), any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("local")));
        fixture.app.probeFailures().toCompletableFuture().join();
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNKNOWN);
        fixture.app.probeFailures().toCompletableFuture().join();
        verify(fixture.peer, times(4)).probe(any(), any(), any(), any());
    }

    @Test
    void synchronousProbeConstructionFailureIsUnknownAndReleasesGate() {
        Fixture fixture = new Fixture();
        when(fixture.peer.probe(any(), any(), any(), any())).thenThrow(new IllegalStateException("local"));
        fixture.app.probeFailures().toCompletableFuture().join();
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNKNOWN);
        fixture.app.probeFailures().toCompletableFuture().join();
        verify(fixture.peer, times(4)).probe(any(), any(), any(), any());
    }

    @Test
    void absentProbeStatusIsUnknown() {
        Fixture fixture = new Fixture();
        when(fixture.peer.probe(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        fixture.app.probeFailures().toCompletableFuture().join();
        assertThat(fixture.row().status()).isEqualTo(BrokerHealthStatus.UNKNOWN);
    }

    private final class Fixture {
        private BrokerCluster cluster = BrokerAutomaticFailureObservationTest.cluster();
        private final AtomicLong nanos = new AtomicLong();
        private final BrokerPeerAdapter peer = mock(BrokerPeerAdapter.class);
        private final BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        private final BrokerClusterAppService app;

        private Fixture() {
            wallClock = TestClock.interceptNow(() -> 100_000L);
            monotonicClock = mockStatic(MonotonicTimestamp.class, CALLS_REAL_METHODS);
            monotonicClock.when(MonotonicTimestamp::now).thenAnswer(ignored -> new MonotonicTimestamp(nanos.get()));
            BrokerRaftAdapter.LocalBroker localBroker = mock(BrokerRaftAdapter.LocalBroker.class);
            when(adapter.getLocalBroker()).thenReturn(localBroker);
            when(localBroker.getId()).thenReturn(new BrokerId("a"));
            when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
            when(adapter.getClusterSnapshot()).thenAnswer(ignored -> cluster);
            when(adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120"), new MembershipEndpoint("c:18120")), Set.of()));
            app = new BrokerClusterAppService(adapter, peer, null, Duration.ofMillis(1), Duration.ofMillis(1), null,
                    Duration.ofSeconds(10),
                    new BrokerHealthObservationTracker(new BrokerId("a"), Duration.ofSeconds(10)),
                    new BrokerFailureObservationService(Duration.ofSeconds(30), Duration.ofSeconds(10)),
                    Duration.ofSeconds(60));
        }

        private BrokerHealthObservationDTO row() {
            return app.getHealthReport(new BrokerHealthReportQuery("cluster-a", "c", "a", cluster.revision().value(),
                    cluster.epoch().value(), "report", List.of("a", "b", "c")))
                    .observations().stream().filter(row -> row.brokerId().equals("b")).findFirst().orElseThrow();
        }
    }
}
