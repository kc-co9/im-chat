package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BrokerObservationStateTest {
    private final Duration cooldown = Duration.ofNanos(60L);
    private final BrokerObservationState state = new BrokerObservationState(cooldown);
    private final BrokerClusterTopology topology = topology(0L);
    private final LeaderTerm term = new LeaderTerm(1L);

    @Test
    void startsColdAndBecomesReadyAtExactThreshold() {
        assertThat(state.advance(topology, term, at(10L))).isFalse();
        assertThat(state.advance(topology, term, at(69L))).isFalse();
        assertThat(state.advance(topology, term, at(70L))).isTrue();
        assertThat(state.advance(topology, term, at(71L))).isTrue();
        assertThat(state.advance(topology, term, at(72L))).isTrue();
    }

    @Test
    void invalidationRestartsAtNextObservationEvenWithSameBackground() {
        state.advance(topology, term, at(0L));
        assertThat(state.advance(topology, term, at(60L))).isTrue();
        state.invalidate();
        assertThat(state.advance(topology, term, at(100L))).isFalse();
        assertThat(state.advance(topology, term, at(159L))).isFalse();
        assertThat(state.advance(topology, term, at(160L))).isTrue();
    }

    @Test
    void changedTermOrTopologyRestartsCooldownUsingValueEquality() {
        state.advance(topology, term, at(0L));
        assertThat(state.advance(topology(0L), new LeaderTerm(1L), at(60L))).isTrue();
        assertThat(state.advance(topology, new LeaderTerm(2L), at(61L))).isFalse();
        assertThat(state.advance(topology, new LeaderTerm(2L), at(121L))).isTrue();
        assertThat(state.advance(topology(1L), new LeaderTerm(2L), at(122L))).isFalse();
        assertThat(state.advance(topology(1L), new LeaderTerm(2L), at(182L))).isTrue();
    }

    @Test
    void consistencyRecoveryStartsFullNewWindow() {
        state.advance(topology, term, at(0L));
        assertThat(state.advance(topology, term, at(60L))).isTrue();
        state.invalidate();
        state.invalidate();
        assertThat(state.advance(topology, term, at(400L))).isFalse();
        assertThat(state.advance(topology, term, at(459L))).isFalse();
        assertThat(state.advance(topology, term, at(460L))).isTrue();
    }

    @Test
    void regressedObservationRestartsFullWindowEvenBeforePreviousOrigin() {
        state.advance(topology, term, at(100L));
        assertThat(state.advance(topology, term, at(160L))).isTrue();
        assertThat(state.advance(topology, term, at(110L))).isFalse();
        assertThat(state.advance(topology, term, at(169L))).isFalse();
        assertThat(state.advance(topology, term, at(170L))).isTrue();
        assertThat(state.advance(topology, term, at(109L))).isFalse();
        assertThat(state.advance(topology, term, at(168L))).isFalse();
        assertThat(state.advance(topology, term, at(169L))).isTrue();
    }

    private static MonotonicTimestamp at(long nanos) {
        return new MonotonicTimestamp(nanos);
    }

    private static BrokerClusterTopology topology(long epoch) {
        return new BrokerClusterTopology(new RoutingEpoch(epoch), List.of(), Map.of(),
                new ConsensusMembership(Set.of(), Set.of()));
    }
}
