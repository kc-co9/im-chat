package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerTrafficState;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;
import com.co.kc.imchat.broker.domain.cluster.model.OperationHistoryCapacity;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.VoterDemotionIntent;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class BrokerClusterConsistentVotersTest {
    private final MembershipEndpoint a = new MembershipEndpoint("a:18120");
    private final MembershipEndpoint b = new MembershipEndpoint("b:18120");

    @Test
    void resolvesOnlyMatchingActualMembershipAndCommittedEndpointRoles() {
        BrokerCluster cluster = cluster(BrokerMembershipRole.LEARNER);
        assertThat(cluster.findConsistentVoters(new ConsensusMembership(Set.of(a), Set.of(b)))).containsExactly(new BrokerId("a"));
        assertThat(cluster.findConsistentVoters(new ConsensusMembership(Set.of(b), Set.of(a)))).isEmpty();
        assertThat(cluster.findConsistentVoters(new ConsensusMembership(Set.of(new MembershipEndpoint("unknown:18120")), Set.of(b)))).isEmpty();
        assertThat(cluster.findConsistentVoters(new ConsensusMembership(Set.of(), Set.of(a, b)))).isEmpty();
        assertThat(cluster.findConsistentVoters(new ConsensusMembership(Set.of(a), Set.of()))).isEmpty();
    }

    @Test
    void overlappingRolesAreRejectedBeforeAggregateResolution() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ConsensusMembership(Set.of(a), Set.of(a, b)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("overlap");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISCOVERED", "LEARNER_SYNCING"})
    void transitionalOrUnqualifiedRoleCannotUseLearnerBinding(String role) {
        assertThat(cluster(BrokerMembershipRole.valueOf(role)).findConsistentVoters(new ConsensusMembership(Set.of(a), Set.of(b)))).isEmpty();
    }

    @Test
    void nonTerminalMembershipOperationBlocksEvenWhenActualRolesStillMatch() {
        BrokerCluster cluster = cluster(BrokerMembershipRole.VOTER);
        ConsensusMembership membership = new ConsensusMembership(Set.of(a, b), Set.of());
        ClusterRevision revision = cluster.revision();
        BrokerClusterTopology topology = cluster.topology(membership);
        assertThat(cluster.findConsistentVoters(membership))
                .containsExactlyInAnyOrder(new BrokerId("a"), new BrokerId("b"));
        assertThat(cluster.demoteVoter(VoterDemotionIntent.builder().operationId(new ClusterOperationId("demote"))
                .commandDigest(new CommandDigest("demote")).expectedRevision(cluster.revision())
                .brokerId(new BrokerId("b")).minimumVoterCount(new MinimumVoterCount(1)).changedAt(Instant.EPOCH).build())
                .accepted()).isTrue();
        assertThat(cluster.revision()).isEqualTo(revision.next());
        assertThat(cluster.topology(membership)).isEqualTo(topology);
        assertThat(cluster.findConsistentVoters(membership)).isEmpty();
    }

    @Test
    void missingBindingFailsClosedAndDiscoveredWithoutBindingDoesNotVote() {
        BrokerCluster cluster = cluster(BrokerMembershipRole.VOTER);
        BrokerCluster missing = BrokerCluster.builder().revision(cluster.revision()).epoch(cluster.epoch())
                .algorithm(cluster.algorithm()).brokers(cluster.brokers()).operations(List.of())
                .operationHistoryCapacity(new OperationHistoryCapacity(100)).membershipEndpoints(Map.of(new BrokerId("a"), a)).build();
        assertThat(missing.findConsistentVoters(new ConsensusMembership(Set.of(a, b), Set.of()))).isEmpty();
        BrokerCluster discovered = BrokerCluster.builder().revision(cluster.revision()).epoch(cluster.epoch())
                .algorithm(cluster.algorithm()).brokers(cluster(BrokerMembershipRole.DISCOVERED).brokers()).operations(List.of())
                .operationHistoryCapacity(new OperationHistoryCapacity(100)).membershipEndpoints(Map.of(new BrokerId("a"), a)).build();
        assertThat(discovered.findConsistentVoters(new ConsensusMembership(Set.of(a), Set.of()))).containsExactly(new BrokerId("a"));
    }

    private BrokerCluster cluster(BrokerMembershipRole secondRole) {
        return BrokerCluster.builder().revision(new ClusterRevision(0L)).epoch(new RoutingEpoch(0L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(new BrokerNode(new BrokerId("a"), new BrokerRouteEndpoint("a:18020"),
                                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE),
                        new BrokerNode(new BrokerId("b"), new BrokerRouteEndpoint("b:18020"), secondRole,
                                secondRole == BrokerMembershipRole.DISCOVERED || secondRole == BrokerMembershipRole.LEARNER_SYNCING
                                        ? BrokerTrafficState.INACTIVE : BrokerTrafficState.READY)))
                .operations(List.of()).operationHistoryCapacity(new OperationHistoryCapacity(100))
                .membershipEndpoints(Map.of(new BrokerId("a"), a, new BrokerId("b"), b)).build();
    }
}
