package com.co.kc.imchat.broker.support.health;

import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterTopology;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import java.util.List;
import java.util.Map;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerTrafficState;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BrokerHealthObservationTrackerTest {
    private final BrokerId reporter = new BrokerId("a");
    private final BrokerId target = new BrokerId("b");
    private final BrokerHealthObservationContext context = context(1L);
    private final BrokerHealthObservationTracker repository =
            new BrokerHealthObservationTracker(reporter, Duration.ofSeconds(10));

    @Test
    void successAndUnknownResetContinuousFailureWindow() {
        repository.synchronize(context);
        record(BrokerHealthStatus.UNHEALTHY, 0);
        record(BrokerHealthStatus.UNHEALTHY, 9);
        assertThat(observation(9).unreachableBeginAt()).isEqualTo(Instant.ofEpochSecond(91));
        record(BrokerHealthStatus.HEALTHY, 10);
        assertThat(observation(10).status()).isEqualTo(BrokerHealthStatus.HEALTHY);
        assertThat(observation(10).unreachableBeginAt()).isNull();
        record(BrokerHealthStatus.UNHEALTHY, 11);
        assertThat(observation(11).unreachableBeginAt()).isEqualTo(Instant.ofEpochSecond(100));
        record(BrokerHealthStatus.UNKNOWN, 12);
        record(BrokerHealthStatus.UNHEALTHY, 13);
        assertThat(observation(13).unreachableBeginAt()).isEqualTo(Instant.ofEpochSecond(100));
    }

    @Test
    void staleSamplesAndSamplingGapsCannotPreserveAnOldFailureWindow() {
        repository.synchronize(context);
        record(BrokerHealthStatus.UNHEALTHY, 0);
        assertThat(observation(11).status()).isEqualTo(BrokerHealthStatus.UNKNOWN);
        record(BrokerHealthStatus.UNHEALTHY, 20);
        assertThat(observation(20).unreachableBeginAt()).isEqualTo(Instant.ofEpochSecond(100));
    }

    @Test
    void topologyChangePrunesStateAndRejectsLateOldContextResults() {
        repository.synchronize(context);
        record(BrokerHealthStatus.UNHEALTHY, 0);
        BrokerHealthObservationContext next = new BrokerHealthObservationContext(
                new ClusterRevision(2L), new BrokerClusterTopology(new RoutingEpoch(1L), List.of(), Map.of(),
                        new ConsensusMembership(Set.of(), Set.of())));
        repository.synchronize(next);
        record(BrokerHealthStatus.UNHEALTHY, 9);
        assertThat(repository.snapshot(next, Instant.EPOCH, at(9))).isEmpty();
        assertThat(repository.snapshot(context, Instant.EPOCH, at(9))).isEmpty();
    }

    private BrokerHealthObservationContext context(long revision) {
        return new BrokerHealthObservationContext(new ClusterRevision(revision),
                new BrokerClusterTopology(new RoutingEpoch(1L), List.of(
                        new BrokerNode(target, new BrokerRouteEndpoint("b:18100"),
                                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE)), Map.of(),
                        new ConsensusMembership(Set.of(), Set.of())));
    }

    @Test
    void eachContextComponentInvalidatesSamplesAndRejectsLateWrites() {
        List<BrokerHealthObservationContext> changedContexts = List.of(
                context(2L),
                new BrokerHealthObservationContext(context.revision(),
                        new BrokerClusterTopology(new RoutingEpoch(2L), context.topology().brokers(), Map.of(),
                                new ConsensusMembership(Set.of(), Set.of()))));
        for (BrokerHealthObservationContext next : changedContexts) {
            repository.synchronize(context);
            record(BrokerHealthStatus.UNHEALTHY, 0);
            repository.synchronize(next);
            record(BrokerHealthStatus.UNHEALTHY, 9);
            assertThat(repository.snapshot(context, Instant.EPOCH, at(9))).isEmpty();
            assertThat(repository.snapshot(next, Instant.EPOCH, at(9)))
                    .allSatisfy(sample -> assertThat(sample.status()).isEqualTo(BrokerHealthStatus.UNKNOWN));
        }
    }

    @Test
    void wallClockRollbackDoesNotExtendContinuousDurationOrReviveStaleSamples() {
        repository.synchronize(context);
        record(BrokerHealthStatus.UNHEALTHY, 0);
        record(BrokerHealthStatus.UNHEALTHY, 9);
        BrokerHealthObservation backwards = repository.snapshot(context, Instant.EPOCH, at(9)).getFirst();
        assertThat(Duration.between(backwards.unreachableBeginAt(), backwards.observedAt()))
                .isEqualTo(Duration.ofSeconds(9));
        assertThat(repository.snapshot(context, Instant.EPOCH, at(20)).getFirst().status())
                .isEqualTo(BrokerHealthStatus.UNKNOWN);
    }

    @Test
    void sampleCompletedAfterRoundStartIsUnknownAtRoundSnapshot() {
        repository.synchronize(context);
        record(BrokerHealthStatus.UNHEALTHY, 0);
        record(BrokerHealthStatus.UNHEALTHY, 9);
        Instant roundAt = Instant.ofEpochSecond(100);
        BrokerHealthObservation snapshot = repository.snapshot(context, roundAt,
                new MonotonicTimestamp(at(9).nanos() - 1L)).getFirst();

        assertThat(snapshot.status()).isEqualTo(BrokerHealthStatus.UNKNOWN);
        assertThat(snapshot.observedAt()).isEqualTo(roundAt);
        assertThat(snapshot.unreachableBeginAt()).isNull();
        assertThat(observation(9).status()).isEqualTo(BrokerHealthStatus.UNHEALTHY);
    }

    @Test
    void onlyActiveTopologyNodesAreTargetsAndMissingSamplesAreUnknown() {
        BrokerNode inactive = new BrokerNode(reporter, new BrokerRouteEndpoint("a:18100"),
                BrokerMembershipRole.VOTER, BrokerTrafficState.INACTIVE);
        BrokerHealthObservationContext mixed = new BrokerHealthObservationContext(context.revision(),
                new BrokerClusterTopology(new RoutingEpoch(1L), List.of(context.topology().brokers().getFirst(), inactive),
                        Map.of(), new ConsensusMembership(Set.of(), Set.of())));
        repository.synchronize(mixed);
        repository.record(mixed, reporter, BrokerHealthStatus.UNHEALTHY, at(0));
        repository.record(mixed, new BrokerId("absent"), BrokerHealthStatus.UNHEALTHY, at(0));
        assertThat(repository.snapshot(mixed, Instant.EPOCH, at(0))).singleElement().satisfies(sample -> {
            assertThat(sample.brokerId()).isEqualTo(target);
            assertThat(sample.status()).isEqualTo(BrokerHealthStatus.UNKNOWN);
        });
    }

    private void record(BrokerHealthStatus status, long seconds) {
        repository.record(context, target, status, at(seconds));
    }

    private BrokerHealthObservation observation(long seconds) {
        return repository.snapshot(context, Instant.ofEpochSecond(100), at(seconds)).getFirst();
    }

    private MonotonicTimestamp at(long seconds) {
        return new MonotonicTimestamp(Duration.ofSeconds(seconds).toNanos());
    }
}
