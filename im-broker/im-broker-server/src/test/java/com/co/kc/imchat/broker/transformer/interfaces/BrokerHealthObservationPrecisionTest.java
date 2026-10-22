package com.co.kc.imchat.broker.transformer.interfaces;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;

import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import java.util.concurrent.CompletableFuture;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthObservationDTO;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerHealthReportQuery;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportRow;
import com.co.kc.imchat.broker.support.TestClock;
import com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.transformer.adapter.BrokerHealthObservationAdapterTransformer;
import com.co.kc.imchat.broker.transformer.application.BrokerHealthObservationAppTransformer;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.provider.EnumSource;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BrokerHealthObservationPrecisionTest {
    @ParameterizedTest
    @EnumSource(BrokerHealthStatus.class)
    void roleNamedIdentitiesMapAcrossAllBoundaries(BrokerHealthStatus status) {
        Instant requestAt = Instant.ofEpochSecond(100);
        BrokerId observer = new BrokerId("authenticated-observer");
        long failureMs = status == BrokerHealthStatus.UNHEALTHY ? 30_000L : 0L;
        BrokerHealthReportRow incoming = new BrokerHealthReportRow("observed-broker",
                com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus.valueOf(status.name()), 10L, failureMs);
        BrokerHealthObservation observation = BrokerHealthObservationAdapterTransformer.INSTANCE
                .observationFrom(incoming, observer, requestAt);
        assertThat(observation.brokerId()).isEqualTo(new BrokerId("observed-broker"));
        assertThat(observation.observerBrokerId()).isEqualTo(observer);
        assertThat(observation.status()).isEqualTo(status);
        assertThat(observation.observedAt()).isEqualTo(requestAt.minusMillis(10));
        assertThat(observation.unreachableBeginAt()).isEqualTo(status == BrokerHealthStatus.UNHEALTHY
                ? requestAt.minusMillis(30_010L) : null);

        BrokerHealthObservationDTO dto = BrokerHealthObservationAppTransformer.INSTANCE.healthObservationFrom(observation, requestAt);
        assertThat(dto.brokerId()).isEqualTo("observed-broker");
        assertThat(dto.status()).isEqualTo(status);
        assertThat(dto.observationAge()).isEqualTo(Duration.ofMillis(10));
        assertThat(dto.unreachableDuration()).isEqualTo(Duration.ofMillis(failureMs));
        BrokerHealthReportRow outgoing = BrokerHealthObservationInterfaceTransformer.INSTANCE.reportRowFrom(dto);
        assertThat(outgoing).isEqualTo(incoming);
        JsonNode wire = JsonUtils.getMapper().valueToTree(outgoing);
        assertThat(wire.size()).isEqualTo(4);
        assertThat(wire.get("brokerId").asText()).isEqualTo("observed-broker");
        assertThat(wire.get("status").asText()).isEqualTo(status.name());
        assertThat(wire.get("observationAgeMs").asLong()).isEqualTo(10L);
        assertThat(wire.get("unreachableDurationMs").asLong()).isEqualTo(failureMs);
        assertThat(wire.has("targetBrokerId")).isFalse();
        assertThat(wire.has("observerBrokerId")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0,0,0", "1,1,0", "999999,1,0", "1000000,1,1", "1000001,2,1"})
    void sdkAgeCeilsAndFailureDurationFloors(long nanos, long expectedAge, long expectedFailure) {
        BrokerHealthObservationDTO row =
                new BrokerHealthObservationDTO("b", BrokerHealthStatus.UNHEALTHY,
                        Duration.ofNanos(nanos), Duration.ofNanos(nanos));
        BrokerHealthReportRow wire =
                BrokerHealthObservationInterfaceTransformer.INSTANCE.reportRowFrom(row);
        assertThat(wire.observationAgeMs()).isEqualTo(expectedAge);
        assertThat(wire.unreachableDurationMs()).isEqualTo(expectedFailure);
    }

    @ParameterizedTest
    @CsvSource({"-1,true", "0,true", "1,false"})
    void freshnessThresholdPropagatesConservativelyThroughWireAndPolicy(long deltaNanos, boolean eligible) {
        assertThat(eligibleAfterWire(Duration.ofSeconds(10).plusNanos(deltaNanos), Duration.ofSeconds(30)))
                .isEqualTo(eligible);
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "1,true"})
    void stabilityThresholdPropagatesConservativelyThroughWireAndPolicy(long deltaNanos, boolean eligible) {
        assertThat(eligibleAfterWire(Duration.ZERO, Duration.ofSeconds(30).plusNanos(deltaNanos)))
                .isEqualTo(eligible);
    }

    private boolean eligibleAfterWire(Duration age, Duration failureDuration) {
        Instant now = Instant.ofEpochSecond(100);
        List<BrokerHealthObservation> observations =
                List.of("b", "c").stream().map(reporter -> {
                    BrokerHealthReportRow wire = BrokerHealthObservationInterfaceTransformer.INSTANCE.reportRowFrom(
                            new BrokerHealthObservationDTO("a", BrokerHealthStatus.UNHEALTHY, age, failureDuration));
                    return BrokerHealthObservationAdapterTransformer.INSTANCE
                            .observationFrom(wire, new BrokerId(reporter), now);
                }).toList();
        return new BrokerFailureObservationService(Duration.ofSeconds(30), Duration.ofSeconds(10))
                .evaluate(new BrokerId("a"), Set.of(new BrokerId("a"), new BrokerId("b"), new BrokerId("c")),
                        observations, now).eligible();
    }

    @Test
    void negativeNanosecondIsRejectedRatherThanClamped() {
        Assertions.assertThatThrownBy(() ->
                new BrokerHealthObservationDTO("b", BrokerHealthStatus.UNHEALTHY,
                        Duration.ofNanos(-1), Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                BrokerHealthObservationInterfaceTransformer.INSTANCE.floorFailureMs(Duration.ofNanos(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oneNanosecondOldObservationMustNotLookNewAtSdkBoundary() {
        RaftBrokerClusterStateMachine machine = new RaftBrokerClusterStateMachine("cluster-a",
                new BrokerClusterState(0L, 0L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                        List.of(new BrokerNode("a", "a:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE),
                                new BrokerNode("b", "b:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE))),
                100, Map.of("a", "a:18120", "b", "b:18120"));
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftAdapter.LocalBroker localBroker = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(localBroker);
        when(localBroker.getId()).thenReturn(new BrokerId("a"));
        when(adapter.getClusterId()).thenReturn(new BrokerClusterId("cluster-a"));
        when(adapter.getClusterSnapshot()).thenReturn(machine.getCluster());
        ConsensusMembership membership = new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120"), new MembershipEndpoint("b:18120")), Set.of());
        when(adapter.getConsensusMembership()).thenReturn(membership);
        AtomicLong nanos = new AtomicLong();
        BrokerHealthObservationTracker store = new BrokerHealthObservationTracker(new BrokerId("a"), Duration.ofSeconds(10));

        BrokerPeerAdapter peer = mock(BrokerPeerAdapter.class);
        when(peer.probe(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BrokerHealthStatus.HEALTHY));
        BrokerClusterAppService app = new BrokerClusterAppService(adapter, peer, null, Duration.ofSeconds(1),
                Duration.ofSeconds(1), null, Duration.ofSeconds(10), store,
                new BrokerFailureObservationService(Duration.ofSeconds(30), Duration.ofSeconds(10)),
                Duration.ofSeconds(60));
        try (MockedStatic<Instant> wallClock = TestClock.interceptNow(() -> 0L);
                MockedStatic<MonotonicTimestamp> monotonicClock = mockStatic(MonotonicTimestamp.class, CALLS_REAL_METHODS)) {
            monotonicClock.when(MonotonicTimestamp::now).thenAnswer(ignored -> new MonotonicTimestamp(nanos.get()));
            app.probeFailures().toCompletableFuture().join();
            nanos.set(1L);
            assertThat(BrokerHealthObservationInterfaceTransformer.INSTANCE.reportResultFrom(app.getHealthReport(
                    new BrokerHealthReportQuery("cluster-a", "b", "a", 0L, 0L, "nonce", List.of("a", "b"))))
                    .observations()).filteredOn(row -> row.brokerId().equals("b"))
                    .singleElement().satisfies(row -> assertThat(row.observationAgeMs()).isEqualTo(1L));
        }
    }
}
