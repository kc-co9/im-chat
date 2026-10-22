package com.co.kc.imchat.broker.support.health;

import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterTopology;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerTrafficState;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerHealthObservationContextTest {
    private final BrokerId a = new BrokerId("a");
    private final BrokerId b = new BrokerId("b");
    private final MembershipEndpoint endpointA = new MembershipEndpoint("Aa:18120");
    private final MembershipEndpoint endpointB = new MembershipEndpoint("BB:18120");

    @Test
    void structuralEqualityIgnoresMapAndSetOrderAndPreservesSamples() {
        Map<BrokerId, MembershipEndpoint> forward = new LinkedHashMap<>();
        forward.put(a, endpointA);
        forward.put(b, endpointB);
        Map<BrokerId, MembershipEndpoint> reverse = new LinkedHashMap<>();
        reverse.put(b, endpointB);
        reverse.put(a, endpointA);
        BrokerHealthObservationContext first = context(forward, List.of(endpointA, endpointB));
        BrokerHealthObservationContext reordered = context(reverse, List.of(endpointB, endpointA));
        assertThat(reordered).isEqualTo(first).hasSameHashCodeAs(first);

        BrokerHealthObservationTracker tracker = new BrokerHealthObservationTracker(a, Duration.ofSeconds(10));
        tracker.synchronize(first);
        tracker.record(first, b, BrokerHealthStatus.UNHEALTHY, new MonotonicTimestamp(0L));
        tracker.synchronize(reordered);
        tracker.record(reordered, b, BrokerHealthStatus.UNHEALTHY,
                new MonotonicTimestamp(Duration.ofSeconds(5).toNanos()));
        assertThat(tracker.snapshot(reordered, Instant.ofEpochSecond(100),
                new MonotonicTimestamp(Duration.ofSeconds(5).toNanos())))
                .filteredOn(sample -> sample.brokerId().equals(b))
                .singleElement().satisfies(sample -> assertThat(Duration.between(
                        sample.unreachableBeginAt(), sample.observedAt())).isEqualTo(Duration.ofSeconds(5)));
    }

    @Test
    void rejectsNullComponents() {
        BrokerClusterTopology topology = context(Map.of(), List.of()).topology();
        assertThatThrownBy(() -> new BrokerHealthObservationContext(null, topology))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("revision is required");
        assertThatThrownBy(() -> new BrokerHealthObservationContext(new ClusterRevision(1L), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("topology is required");
    }

    private BrokerHealthObservationContext context(Map<BrokerId, MembershipEndpoint> endpoints,
            List<MembershipEndpoint> voters) {
        BrokerClusterTopology topology = new BrokerClusterTopology(new RoutingEpoch(1L), List.of(
                new BrokerNode(a, new BrokerRouteEndpoint("a:18100"), BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE),
                new BrokerNode(b, new BrokerRouteEndpoint("b:18100"), BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE)), endpoints,
                new ConsensusMembership(new LinkedHashSet<>(voters), Set.of()));
        return new BrokerHealthObservationContext(new ClusterRevision(1L), topology);
    }
}
