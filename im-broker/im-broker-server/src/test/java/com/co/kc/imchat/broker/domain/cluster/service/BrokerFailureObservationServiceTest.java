package com.co.kc.imchat.broker.domain.cluster.service;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerFailureObservationDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerFailureObservationServiceTest {

    private final BrokerFailureObservationService policy = new BrokerFailureObservationService(
            Duration.ofSeconds(30), Duration.ofSeconds(10),
            Duration.ofSeconds(60));

    @Test
    void acceptsOnlyFreshStableReportsFromMajorityOfCurrentVoters() {
        Instant now = at(100_000L);
        Set<BrokerId> voters = voters();
        List<BrokerHealthObservation> observations = List.of(
                unreachable("broker-a", "broker-b", 60_000L, 99_000L),
                unreachable("broker-a", "broker-c", 60_000L, 99_500L));

        BrokerFailureObservationDecision decision = policy.evaluate(
                broker("broker-a"), voters, observations, now, at(1_000L));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.reportingVoters()).containsExactly(
                broker("broker-b"), broker("broker-c"));
        assertThat(decision.requiredReports().value()).isEqualTo(2);
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
                broker("broker-a"), voters, observations, now, at(1_000L));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reportingVoters()).containsExactly(broker("broker-b"));
        assertThat(decision.reason().value()).contains("majority");
    }

    @Test
    void rejectsDuringTopologyCooldownAndNeverCountsTargetSelfReport() {
        Instant now = at(100_000L);
        Set<BrokerId> voters = voters();
        List<BrokerHealthObservation> observations = List.of(
                unreachable("broker-a", "broker-a", 60_000L, 99_000L),
                unreachable("broker-a", "broker-b", 60_000L, 99_000L),
                unreachable("broker-a", "broker-c", 60_000L, 99_000L));

        BrokerFailureObservationDecision decision = policy.evaluate(
                broker("broker-a"), voters, observations, now, at(70_000L));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reportingVoters()).containsExactly(
                broker("broker-b"), broker("broker-c"));
        assertThat(decision.reason().value()).contains("cooldown");
    }

    private BrokerHealthObservation unreachable(String target, String reporter,
                                                 long unreachableSince, long observedAt) {
        return BrokerHealthObservation.builder()
                .targetBrokerId(broker(target))
                .reporterBrokerId(broker(reporter))
                .unreachableSince(at(unreachableSince))
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
