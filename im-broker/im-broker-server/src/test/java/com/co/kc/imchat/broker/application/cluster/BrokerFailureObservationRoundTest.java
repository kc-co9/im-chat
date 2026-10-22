package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;

import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerTrafficState;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerPromotionIntent;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;
import com.co.kc.imchat.broker.domain.cluster.model.OperationHistoryCapacity;
import com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex;
import com.co.kc.imchat.broker.domain.cluster.model.VoterDemotionIntent;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerHealthReportQuery;
import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import com.co.kc.imchat.broker.support.health.BrokerHealthObservationContext;
import com.co.kc.imchat.broker.transformer.adapter.BrokerHealthObservationAdapterTransformer;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportRow;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrokerFailureObservationRoundTest {
    private MockedStatic<Instant> wallClock;
    private MockedStatic<MonotonicTimestamp> monotonicClock;

    @AfterEach
    void releaseTimeScopes() {
        monotonicClock.close();
        wallClock.close();
    }

    @Test
    void inconsistentMembershipDoesNotRequestReportsOrQuorum() {
        Fixture fixture = new Fixture();
        when(fixture.adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(
                Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120")),
                Set.of(new MembershipEndpoint("c:18120"))));
        fixture.app.reconcileFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(120).toNanos());
        fixture.app.reconcileFailures().toCompletableFuture().join();
        verify(fixture.adapter, never()).confirmLeadership(any());
        verifyNoInteractions(fixture.peer);
    }

    @Test
    void readyButBlockedRoundDoesNotRequestReportsOrQuorum() {
        Fixture fixture = new Fixture();
        BrokerCluster blocked = spy(fixture.cluster);
        when(blocked.hasBlockingOperation()).thenReturn(true);
        when(fixture.adapter.getClusterSnapshot()).thenReturn(blocked);
        fixture.app.reconcileFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(60).toNanos());
        fixture.app.reconcileFailures().toCompletableFuture().join();
        verify(fixture.adapter, never()).confirmLeadership(any());
        verifyNoInteractions(fixture.peer);
    }

    @Test
    void termSwitchWhileReportsArePendingDiscardsRoundAndRequiresFullCooldown() {
        Fixture fixture = new Fixture();
        fixture.app.reconcileFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(60).toNanos());
        CompletableFuture<List<BrokerHealthObservation>> pending = fixture.pendingReport();
        CompletableFuture<Void> round = fixture.app.reconcileFailures().toCompletableFuture();
        when(fixture.adapter.getLocalBroker().getLeaderTerm()).thenReturn(Optional.of(new LeaderTerm(2L)));
        when(fixture.adapter.confirmLeadership(any())).thenReturn(CompletableFuture.completedFuture(
                new QuorumRead(new LeaderTerm(2L), new QuorumReadIndex(2L))));
        pending.complete(List.of());
        round.join();
        verify(fixture.policy, never()).evaluate(any(), any(), any(), any());
        verify(fixture.adapter, never()).beginOperation(any());
        clearInvocations(fixture.peer, fixture.adapter);
        fixture.nanos.set(Duration.ofSeconds(61).toNanos());
        fixture.app.reconcileFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(121).toNanos() - 1L);
        fixture.app.reconcileFailures().toCompletableFuture().join();
        verifyNoInteractions(fixture.peer);
        verify(fixture.adapter, never()).confirmLeadership(any());
        verify(fixture.policy, never()).evaluate(any(), any(), any(), any());
        fixture.nanos.incrementAndGet();
        fixture.app.reconcileFailures().toCompletableFuture().join();
        verify(fixture.peer, times(2)).fetchHealthReport(any(), any(), any(), any(), any());
        verify(fixture.adapter).confirmLeadership(any());
    }

    @Test
    void coldAndCoolingRoundsDoNotEvaluateEvidenceOrRequestQuorum() {
        Fixture fixture = new Fixture();
        fixture.app.reconcileFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(60).toNanos() - 1L);
        fixture.app.reconcileFailures().toCompletableFuture().join();
        verify(fixture.adapter, never()).confirmLeadership(any());
        verifyNoInteractions(fixture.peer);
        verify(fixture.policy, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void roundRemainsOccupiedUntilCrossThreadFailureAndCanThenRestart() throws Exception {
        Fixture fixture = new Fixture();
        fixture.app.reconcileFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(60).toNanos());
        CompletableFuture<QuorumRead> pending = new CompletableFuture<>();
        when(fixture.adapter.confirmLeadership(any())).thenReturn(pending);
        CompletableFuture<Void> firstRound = fixture.app.reconcileFailures().toCompletableFuture();

        assertThat(firstRound).isNotDone();
        assertThat(fixture.app.reconcileFailures().toCompletableFuture()).isCompleted();
        verify(fixture.adapter, times(1)).confirmLeadership(any());

        CompletableFuture<Void> completion = CompletableFuture.runAsync(() ->
                pending.completeExceptionally(new IllegalStateException("quorum unavailable")));
        completion.get(5, java.util.concurrent.TimeUnit.SECONDS);
        org.assertj.core.api.Assertions.assertThatThrownBy(firstRound::join)
                .hasCauseInstanceOf(IllegalStateException.class);

        CompletableFuture<QuorumRead> next = new CompletableFuture<>();
        when(fixture.adapter.confirmLeadership(any())).thenReturn(next);
        CompletableFuture<Void> nextRound = fixture.app.reconcileFailures().toCompletableFuture();
        assertThat(nextRound).isNotDone();
        verify(fixture.adapter, times(2)).confirmLeadership(any());
        next.completeExceptionally(new IllegalStateException("quorum unavailable again"));
        org.assertj.core.api.Assertions.assertThatThrownBy(nextRound::join)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void observedMembershipMismatchRestartsCooldownEvenWhenOriginalTopologyReturns() {
        Fixture fixture = new Fixture();
        fixture.app.reconcileFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(60).toNanos());
        CompletableFuture<List<BrokerHealthObservation>> pending = fixture.pendingReport();
        CompletableFuture<Void> round = fixture.app.reconcileFailures().toCompletableFuture();
        when(fixture.adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120")), Set.of(new MembershipEndpoint("c:18120"))));
        pending.complete(List.of());
        round.join();
        when(fixture.adapter.getConsensusMembership()).thenReturn(fixture.membership);
        fixture.nanos.set(Duration.ofSeconds(61).toNanos());
        clearInvocations(fixture.peer);
        fixture.app.reconcileFailures().toCompletableFuture().join();
        verify(fixture.peer, never()).fetchHealthReport(any(), any(), any(), any(), any());
        doReturn(CompletableFuture.completedFuture(List.of())).when(fixture.peer).fetchHealthReport(any(), any(), any(), any(), any());
        fixture.nanos.set(Duration.ofSeconds(121).toNanos());
        fixture.app.reconcileFailures().toCompletableFuture().join();
        verify(fixture.peer, times(2)).fetchHealthReport(any(), any(), any(), any(), any());
    }

    @Test
    void overlappingMembershipInitialAndAsyncReadsReleaseRoundGateWithoutAutomaticBegin() {
        Fixture fixture = new Fixture();
        when(fixture.adapter.getConsensusMembership()).thenAnswer(ignored -> new ConsensusMembership(
                Set.of(new MembershipEndpoint("a:18120")), Set.of(new MembershipEndpoint("a:18120"))));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.app.reconcileFailures().toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class);
        verifyNoInteractions(fixture.peer);
        doReturn(fixture.membership).when(fixture.adapter).getConsensusMembership();
        fixture.app.reconcileFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(60).toNanos());
        CompletableFuture<List<BrokerHealthObservation>> pending = fixture.pendingReport();
        CompletableFuture<Void> round = fixture.app.reconcileFailures().toCompletableFuture();
        when(fixture.adapter.getConsensusMembership()).thenAnswer(ignored -> new ConsensusMembership(
                Set.of(new MembershipEndpoint("a:18120")), Set.of(new MembershipEndpoint("a:18120"))));
        pending.complete(List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(round::join)
                .hasCauseInstanceOf(IllegalStateException.class);
        verify(fixture.adapter, never()).beginOperation(any());
        assertThat(fixture.cluster.operations()).isEmpty();
        doReturn(fixture.membership).when(fixture.adapter).getConsensusMembership();
        doReturn(CompletableFuture.completedFuture(List.of())).when(fixture.peer).fetchHealthReport(any(), any(), any(), any(), any());
        clearInvocations(fixture.peer);
        fixture.nanos.set(Duration.ofSeconds(61).toNanos());
        fixture.app.reconcileFailures().toCompletableFuture().join();
        verify(fixture.peer, times(2)).fetchHealthReport(any(), any(), any(), any(), any());
        verify(fixture.adapter, never()).beginOperation(any());
    }

    @ParameterizedTest
    @CsvSource({"0,30000,true", "1,30000,false", "0,29999,false"})
    void delayedReportsUseRequestTimeAndPreserveFreshnessAndStableWindowBoundaries(long extraNanos, long unreachableDurationMs, boolean eligible) {
        Fixture fixture = new Fixture();
        fixture.app.reconcileFailures().toCompletableFuture().join();
        BrokerHealthObservationContext context = new BrokerHealthObservationContext(
                fixture.cluster.revision(), fixture.cluster.topology(fixture.membership));
        fixture.store.synchronize(context);
        for (long second = 20; second <= 60; second += 5) {
            fixture.store.record(context, new BrokerId("b"), BrokerHealthStatus.UNHEALTHY,
                    new MonotonicTimestamp(Duration.ofSeconds(second).toNanos()));
        }
        fixture.nanos.set(Duration.ofSeconds(60).toNanos());
        when(fixture.adapter.getConsensusMembership()).thenAnswer(call -> {
            fixture.nanos.addAndGet(Duration.ofMillis(100).toNanos());
            return fixture.membership;
        });
        List<Instant> requestBaselines = new ArrayList<>();
        List<BrokerHealthObservation> remoteReports = new ArrayList<>();
        when(fixture.peer.fetchHealthReport(any(), any(), any(), any(), any())).thenAnswer(call -> {
            BrokerNode reporter = call.getArgument(0);
            fixture.wallMillis.set(100_000L + Duration.ofNanos(fixture.nanos.get()
                    - Duration.ofSeconds(60).toNanos()).toMillis());
            fixture.wallExtraNanos.set(0L);
            Instant requestAt = Instant.now();
            requestBaselines.add(requestAt);
            fixture.nanos.addAndGet(Duration.ofMillis(50).toNanos() + (requestBaselines.size() == 2 ? extraNanos : 0L));
            fixture.wallMillis.set(100_200);
            fixture.wallExtraNanos.set(extraNanos);
            if (!reporter.id().equals(new BrokerId("c"))) {
                return CompletableFuture.completedFuture(List.of());
            }
            BrokerHealthObservation report = BrokerHealthObservationAdapterTransformer.INSTANCE.observationFrom(
                    new BrokerHealthReportRow("b", com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus.UNHEALTHY,
                            Duration.between(Instant.ofEpochMilli(90_200), requestAt).toMillis(),
                            unreachableDurationMs), reporter.id(), requestAt);
            remoteReports.add(report);
            return CompletableFuture.completedFuture(List.of(report));
        });

        fixture.app.reconcileFailures().toCompletableFuture().join();

        assertThat(requestBaselines).containsExactly(Instant.ofEpochMilli(100_100), Instant.ofEpochMilli(100_150));
        assertThat(remoteReports).singleElement().satisfies(report -> {
            assertThat(report.observedAt()).isEqualTo(Instant.ofEpochMilli(90_200));
            assertThat(Duration.between(report.unreachableBeginAt(), report.observedAt())).isEqualTo(Duration.ofMillis(unreachableDurationMs));
        });
        verify(fixture.adapter, times(eligible ? 1 : 0)).beginOperation(any());
    }

    @ParameterizedTest
    @CsvSource({"10100,99800,true", "9900,100200,false", "9900,90000,false"})
    void finalWallSampleControlsFreshnessIncludingRollbackAndFutureReportRejection(long ageMs, long evaluatedAtMs, boolean eligible) {
        Fixture fixture = new Fixture();
        fixture.app.getHealthReport(new BrokerHealthReportQuery("cluster-a", "c", "a", 0L, 0L, "seed", List.of("a", "b", "c")));
        when(fixture.peer.probe(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BrokerHealthStatus.UNHEALTHY));
        for (long second = 0; second <= 50; second += 5) {
            fixture.nanos.set(Duration.ofSeconds(second).toNanos());
            fixture.app.probeFailures().toCompletableFuture().join();
        }
        fixture.nanos.set(Duration.ofMillis(50_100).toNanos());
        fixture.app.probeFailures().toCompletableFuture().join();
        fixture.nanos.set(0L);
        fixture.app.reconcileFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(60).toNanos());
        CompletableFuture<List<BrokerHealthObservation>> pending = fixture.pendingReport();
        CompletableFuture<Void> round = fixture.app.reconcileFailures().toCompletableFuture();
        // 回拨允许原本将过期的报告继续有效，但未来报告仍不参与多数。
        Instant requestAt = Instant.now();
        BrokerHealthObservation report = BrokerHealthObservationAdapterTransformer.INSTANCE.observationFrom(
                new BrokerHealthReportRow("b", com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus.UNHEALTHY,
                        ageMs, 40_100L), new BrokerId("c"), requestAt);
        fixture.nanos.set(Duration.ofMillis(60_200).toNanos());
        fixture.wallMillis.set(evaluatedAtMs);
        pending.complete(List.of(report));
        round.join();
        verify(fixture.adapter, times(eligible ? 1 : 0)).beginOperation(any());
        assertThat(Duration.between(report.unreachableBeginAt(), report.observedAt())).isEqualTo(Duration.ofMillis(40_100));
    }

    @ParameterizedTest
    @CsvSource({"-200,9600", "200,10000"})
    void wallJumpAfterRequestSamplingCanOverOrUnderestimateAge(long jumpMs, long effectiveAgeMs) {
        Fixture fixture = new Fixture();
        Instant requestAt = Instant.now();
        BrokerHealthObservation report = BrokerHealthObservationAdapterTransformer.INSTANCE.observationFrom(
                new BrokerHealthReportRow("b", com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus.UNHEALTHY,
                        9_800L, 30_000L), new BrokerId("c"), requestAt);
        fixture.wallMillis.addAndGet(jumpMs);
        Instant evaluatedAt = Instant.now();
        assertThat(Duration.between(report.observedAt(), evaluatedAt)).isEqualTo(Duration.ofMillis(effectiveAgeMs));
        assertThat(Duration.between(report.unreachableBeginAt(), report.observedAt())).isEqualTo(Duration.ofSeconds(30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROMOTE_FIRST_STEP", "DEMOTE_FIRST_STEP", "MEMBERSHIP_APPLIED_COMMIT_PENDING"})
    void membershipTransitionCannotInitiateAutomaticFailure(String phase) {
        Fixture fixture = new Fixture();
        fixture.app.reconcileFailures().toCompletableFuture().join();
        BrokerCluster transitional = BrokerAutomaticFailureObservationTest.cluster();
        if (phase.equals("PROMOTE_FIRST_STEP")) {
            transitional = BrokerCluster.builder().revision(transitional.revision()).epoch(transitional.epoch())
                    .algorithm(transitional.algorithm()).operations(List.of())
                    .operationHistoryCapacity(new OperationHistoryCapacity(100))
                    .membershipEndpoints(transitional.membershipEndpoints())
                    .brokers(transitional.brokers().stream().map(node -> node.id().value().equals("c")
                            ? new BrokerNode(node.id(), node.routeEndpoint(),
                                    BrokerMembershipRole.LEARNER,
                                    BrokerTrafficState.READY) : node).toList()).build();
            assertThat(transitional.promoteLearner(LearnerPromotionIntent.builder()
                    .operationId(new ClusterOperationId("promote"))
                    .commandDigest(new CommandDigest("promote"))
                    .expectedRevision(transitional.revision()).brokerId(new BrokerId("c")).changedAt(Instant.EPOCH).build()).accepted()).isTrue();
            when(fixture.adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120")), Set.of(new MembershipEndpoint("c:18120"))));
        } else {
            assertThat(transitional.demoteVoter(VoterDemotionIntent.builder()
                    .operationId(new ClusterOperationId("demote"))
                    .commandDigest(new CommandDigest("demote"))
                    .expectedRevision(transitional.revision()).brokerId(new BrokerId("c"))
                    .minimumVoterCount(new MinimumVoterCount(1))
                    .changedAt(Instant.EPOCH).build()).accepted()).isTrue();
            if (phase.equals("MEMBERSHIP_APPLIED_COMMIT_PENDING")) {
                when(fixture.adapter.getConsensusMembership()).thenReturn(new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120")), Set.of(new MembershipEndpoint("c:18120"))));
            }
        }
        when(fixture.adapter.getClusterSnapshot()).thenReturn(transitional);
        fixture.nanos.set(Duration.ofSeconds(60).toNanos());
        fixture.app.reconcileFailures().toCompletableFuture().join();
        verify(fixture.adapter, never()).beginOperation(any());
        verify(fixture.peer, never()).fetchHealthReport(any(), any(), any(), any(), any());
        when(fixture.adapter.getClusterSnapshot()).thenReturn(fixture.cluster);
        when(fixture.adapter.getConsensusMembership()).thenReturn(fixture.membership);
        fixture.nanos.set(Duration.ofSeconds(61).toNanos());
        fixture.app.reconcileFailures().toCompletableFuture().join();
        verify(fixture.peer, never()).fetchHealthReport(any(), any(), any(), any(), any());
        fixture.nanos.set(Duration.ofSeconds(121).toNanos());
        fixture.app.reconcileFailures().toCompletableFuture().join();
        verify(fixture.peer, times(2)).fetchHealthReport(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"60100,UNHEALTHY", "60201,UNKNOWN", "49999,UNKNOWN"})
    void localSnapshotUsesFreshSampleAfterRoundStartAndRejectsFutureOrStaleSamples(long sampleMs, BrokerHealthStatus expected) {
        Fixture fixture = new Fixture();
        fixture.app.reconcileFailures().toCompletableFuture().join();
        BrokerHealthObservationContext context = new BrokerHealthObservationContext(
                fixture.cluster.revision(), fixture.cluster.topology(fixture.membership));
        fixture.store.synchronize(context);
        fixture.nanos.set(Duration.ofSeconds(60).toNanos());
        when(fixture.adapter.getConsensusMembership()).thenAnswer(call -> {
            fixture.store.record(context, new BrokerId("b"), BrokerHealthStatus.UNHEALTHY,
                    new MonotonicTimestamp(Duration.ofMillis(sampleMs).toNanos()));
            fixture.nanos.set(Duration.ofMillis(60_200).toNanos());
            fixture.wallMillis.set(100_200);
            return fixture.membership;
        });

        fixture.app.reconcileFailures().toCompletableFuture().join();

        ArgumentCaptor<java.util.Collection<BrokerHealthObservation>> reports = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(fixture.policy).evaluate(eq(new BrokerId("b")), any(), reports.capture(), any());
        assertThat(reports.getValue()).filteredOn(report -> report.brokerId().equals(new BrokerId("b")))
                .singleElement().satisfies(report -> {
                    assertThat(report.status()).isEqualTo(expected);
                    assertThat(report.observedAt()).isEqualTo(expected == BrokerHealthStatus.UNKNOWN
                            ? Instant.ofEpochMilli(100_200) : Instant.ofEpochMilli(100_100));
                    if (expected == BrokerHealthStatus.UNHEALTHY) {
                        assertThat(report.unreachableBeginAt()).isEqualTo(report.observedAt());
                    }
                });
    }

    @Test
    void allTargetsShareSingleFinalWallSampleEvenWhenEvaluationAdvancesWallClock() {
        Fixture fixture = new Fixture();
        fixture.app.reconcileFailures().toCompletableFuture().join();
        fixture.nanos.set(Duration.ofSeconds(60).toNanos());
        CompletableFuture<List<BrokerHealthObservation>> pending = fixture.pendingReport();
        CompletableFuture<Void> round = fixture.app.reconcileFailures().toCompletableFuture();
        fixture.wallMillis.set(100_250);
        doAnswer(call -> {
            fixture.wallMillis.incrementAndGet();
            return call.callRealMethod();
        }).when(fixture.policy).evaluate(any(), any(), any(), any());
        pending.complete(List.of());
        round.join();

        ArgumentCaptor<Instant> evaluatedAt = ArgumentCaptor.forClass(Instant.class);
        verify(fixture.policy, times(3)).evaluate(any(), any(), any(), evaluatedAt.capture());
        assertThat(evaluatedAt.getAllValues()).containsExactly(
                Instant.ofEpochMilli(100_250), Instant.ofEpochMilli(100_250), Instant.ofEpochMilli(100_250));
    }

    @ParameterizedTest
    @CsvSource({"600,true", "11000,false"})
    void slowRoundEvaluatesReportsButOnlyFreshMajorityCanBegin(long elapsedMs, boolean eligible) {
        Fixture fixture = new Fixture();
        fixture.app.reconcileFailures().toCompletableFuture().join();
        BrokerHealthObservationContext context = new BrokerHealthObservationContext(
                fixture.cluster.revision(), fixture.cluster.topology(fixture.membership));
        fixture.store.synchronize(context);
        for (long second = 20; second <= 60; second += 5) {
            fixture.store.record(context, new BrokerId("b"), BrokerHealthStatus.UNHEALTHY,
                    new MonotonicTimestamp(Duration.ofSeconds(second).toNanos()));
        }
        fixture.nanos.set(Duration.ofSeconds(60).toNanos());
        CompletableFuture<List<BrokerHealthObservation>> pending = fixture.pendingReport();
        CompletableFuture<Void> round = fixture.app.reconcileFailures().toCompletableFuture();
        assertThat(round).isNotDone();
        Instant reportedAt = Instant.now();
        BrokerHealthObservation report = BrokerHealthObservation.builder()
                .brokerId(new BrokerId("b")).observerBrokerId(new BrokerId("c"))
                .status(BrokerHealthStatus.UNHEALTHY).observedAt(reportedAt)
                .unreachableBeginAt(reportedAt.minusSeconds(30)).build();
        fixture.nanos.addAndGet(Duration.ofMillis(elapsedMs).toNanos());
        fixture.wallMillis.addAndGet(elapsedMs);
        pending.complete(List.of(report));
        round.join();

        Instant evaluatedAt = reportedAt.plusMillis(elapsedMs);
        verify(fixture.policy).evaluate(eq(new BrokerId("b")), any(), any(), eq(evaluatedAt));
        verify(fixture.adapter, times(eligible ? 1 : 0)).beginOperation(any());
    }

    private final class Fixture {
        private final BrokerCluster cluster = BrokerAutomaticFailureObservationTest.cluster();
        private final ConsensusMembership membership = new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120"), new MembershipEndpoint("c:18120")), Set.of());
        private final BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        private final BrokerPeerAdapter peer = mock(BrokerPeerAdapter.class);
        private final AtomicLong nanos = new AtomicLong();
        private final AtomicLong wallMillis = new AtomicLong(100_000);
        private final AtomicLong wallExtraNanos = new AtomicLong();
        private final BrokerHealthObservationTracker store = new BrokerHealthObservationTracker(new BrokerId("a"), Duration.ofSeconds(10));
        private final BrokerFailureObservationService policy = spy(new BrokerFailureObservationService(
                Duration.ofSeconds(30), Duration.ofSeconds(10)));
        private final BrokerClusterAppService app;

        private Fixture() {
            wallClock = mockStatic(Instant.class, CALLS_REAL_METHODS);
            wallClock.when(Instant::now).thenAnswer(ignored ->
                    Instant.ofEpochMilli(wallMillis.get()).plusNanos(wallExtraNanos.get()));
            monotonicClock = mockStatic(MonotonicTimestamp.class, CALLS_REAL_METHODS);
            monotonicClock.when(MonotonicTimestamp::now).thenAnswer(ignored -> new MonotonicTimestamp(nanos.get()));
            when(BrokerRaftTestBindings.bindLocalBroker(adapter).isLeader()).thenReturn(true);
            when(adapter.getLocalBroker().getId()).thenReturn(new BrokerId("a"));
            when(adapter.getLocalBroker().getLeaderTerm()).thenReturn(Optional.of(new LeaderTerm(1L)));
            when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
            when(adapter.getClusterSnapshot()).thenReturn(cluster);
            when(adapter.getConsensusMembership()).thenReturn(membership);
            when(adapter.confirmLeadership(any())).thenReturn(CompletableFuture.completedFuture(
                    new QuorumRead(new LeaderTerm(1L), new QuorumReadIndex(1L))));
            when(adapter.beginOperation(any())).thenReturn(CompletableFuture.completedFuture(BrokerClusterConsensusResult.rejected("test must not submit")));
            when(peer.fetchHealthReport(any(), any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
            app = new BrokerClusterAppService(adapter, peer, mock(GatewayRegistry.class), Duration.ofMillis(1), Duration.ofMillis(1),
                    null, Duration.ofSeconds(10), store,
                    policy,
                    Duration.ofSeconds(60));
        }

        private CompletableFuture<List<BrokerHealthObservation>> pendingReport() {
            CompletableFuture<List<BrokerHealthObservation>> pending = new CompletableFuture<>();
            when(peer.fetchHealthReport(any(), any(), any(), any(), any())).thenAnswer(call -> {
                BrokerNode node = call.getArgument(0);
                return node.id().value().equals("c") ? pending : CompletableFuture.completedFuture(List.of());
            });
            return pending;
        }
    }
}
