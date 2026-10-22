package com.co.kc.imchat.broker.domain.cluster.service;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerFailureObservationDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerFailureObservationServiceTest {

    private final BrokerFailureObservationService policy = new BrokerFailureObservationService(
            Duration.ofSeconds(30), Duration.ofSeconds(10));

    @Test
    void acceptsOnlyFreshStableReportsFromMajorityOfCurrentVoters() {
        Instant now = at(100_000L);
        Set<BrokerId> voters = voters();
        List<BrokerHealthObservation> observations = List.of(
                unreachable("broker-a", "broker-b", 60_000L, 99_000L),
                unreachable("broker-a", "broker-c", 60_000L, 99_500L));

        BrokerFailureObservationDecision decision = policy.evaluate(
                broker("broker-a"), voters, observations, now);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.observerIds()).containsExactly(
                broker("broker-b"), broker("broker-c"));
        assertThat(decision.quorum().value()).isEqualTo(2);
    }

    @Test
    void rejectsTransientStaleDuplicateAndNonVoterReports() {
        Instant now = at(100_000L);
        Set<BrokerId> voters = voters();
        List<BrokerHealthObservation> observations = List.of(
                unreachable("broker-a", "broker-b", 80_000L, 99_000L),
                unreachable("broker-a", "broker-b", 60_000L, 99_500L),
                unreachable("broker-a", "broker-c", 50_000L, 80_000L),
                unreachable("broker-a", "broker-x", 60_000L, 99_000L));

        BrokerFailureObservationDecision decision = policy.evaluate(
                broker("broker-a"), voters, observations, now);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.observerIds()).containsExactly(broker("broker-b"));
        assertThat(decision.reason().value()).contains("majority");
    }

    @Test
    void acceptsReportMajorityWithoutCountingTargetSelfReport() {
        Instant now = at(100_000L);
        Set<BrokerId> voters = voters();
        List<BrokerHealthObservation> observations = List.of(
                unreachable("broker-a", "broker-a", 60_000L, 99_000L),
                unreachable("broker-a", "broker-b", 60_000L, 99_000L),
                unreachable("broker-a", "broker-c", 60_000L, 99_000L));

        BrokerFailureObservationDecision decision = policy.evaluate(
                broker("broker-a"), voters, observations, now);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.observerIds()).containsExactly(
                broker("broker-b"), broker("broker-c"));
        assertThat(decision.quorum().value()).isEqualTo(2);
        assertThat(decision.reason()).isNull();
    }

    @Test
    void latestShortFailureReplacesEarlierStableFailureFromSameReporter() {
        List<BrokerHealthObservation> observations = List.of(
                unreachable("broker-a", "broker-b", 60_000L, 98_000L),
                unreachable("broker-a", "broker-b", 99_000L, 99_500L),
                unreachable("broker-a", "broker-c", 60_000L, 99_500L));

        BrokerFailureObservationDecision decision = policy.evaluate(
                broker("broker-a"), voters(), observations, at(100_000L));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.observerIds()).containsExactly(broker("broker-c"));
        assertThat(decision.reason().value()).contains("majority");
    }

    @Test
    void equalTimestampConflictingReportsNeverPickAnUnhealthyVote() {
        BrokerHealthObservation recovered = BrokerHealthObservation.builder().brokerId(broker("broker-a"))
                .observerBrokerId(broker("broker-b")).status(BrokerHealthStatus.HEALTHY).observedAt(at(99_000L)).build();
        List<BrokerHealthObservation> observations = List.of(
                unreachable("broker-a", "broker-b", 60_000L, 99_000L), recovered,
                unreachable("broker-a", "broker-c", 60_000L, 99_000L));
        assertThat(policy.evaluate(broker("broker-a"), voters(), observations, at(100_000L))
                .observerIds()).containsExactly(broker("broker-c"));
    }

    @Test
    void stableWindowAcceptsExactThresholdButNotOneMillisecondEarlier() {
        List<BrokerHealthObservation> stable = List.of(
                unreachable("broker-a", "broker-b", 60_000L, 90_000L),
                unreachable("broker-a", "broker-c", 60_000L, 90_000L));
        assertThat(policy.evaluate(broker("broker-a"), voters(), stable, at(100_000L))
                .eligible()).isTrue();
        List<BrokerHealthObservation> early = List.of(
                unreachable("broker-a", "broker-b", 60_001L, 90_000L),
                unreachable("broker-a", "broker-c", 60_001L, 90_000L));
        assertThat(policy.evaluate(broker("broker-a"), voters(), early, at(100_000L))
                .eligible()).isFalse();
    }

    @Test
    void targetSelfReportCannotCompleteMajority() {
        List<BrokerHealthObservation> observations = List.of(
                unreachable("broker-a", "broker-a", 60_000L, 99_000L),
                unreachable("broker-a", "broker-b", 60_000L, 99_000L));

        BrokerFailureObservationDecision decision = policy.evaluate(
                broker("broker-a"), voters(), observations, at(100_000L));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.observerIds()).containsExactly(broker("broker-b"));
        assertThat(decision.quorum().value()).isEqualTo(2);
        assertThat(decision.reason().value()).contains("majority");
    }

    @Test
    void rejectsMissingObservationsOrEvaluationTime() {
        assertThatThrownBy(() -> policy.evaluate(broker("broker-a"), voters(), null, at(100_000L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluate(broker("broker-a"), voters(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BrokerHealthObservation unreachable(String target, String reporter,
                                                 long unreachableBeginAt, long observedAt) {
        return BrokerHealthObservation.builder()
                .status(BrokerHealthStatus.UNHEALTHY)
                .brokerId(broker(target))
                .observerBrokerId(broker(reporter))
                .unreachableBeginAt(at(unreachableBeginAt))
                .observedAt(at(observedAt))
                .build();
    }

    private Instant at(long epochMilli) {
        return Instant.ofEpochMilli(epochMilli);
    }

    private Set<BrokerId> voters() {
        return Set.of(broker("broker-a"), broker("broker-b"), broker("broker-c"));
    }

    private BrokerId broker(String value) {
        return new BrokerId(value);
    }
}
