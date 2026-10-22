package com.co.kc.imchat.broker.transformer.adapter;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.plugin.raft.model.RaftMembership;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import org.junit.jupiter.api.Test;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerClusterStateTransformerTest {

    private final BrokerClusterStateTransformer transformer =
            BrokerClusterStateTransformer.INSTANCE;

    @Test
    void membershipEndpointMappingPreservesOpaqueValue() {
        assertThat(transformer.membershipEndpointFrom(" host:18120:2:100 "))
                .isEqualTo(new MembershipEndpoint(" host:18120:2:100 "));
    }

    @Test
    void mapsBothMembershipListsToSetsNormalizingDuplicatesAndPreservingOpaqueStrings() {
        RaftMembership raw = new RaftMembership(
                List.of("z:18120:2:90", " host:18120:3:100 ", "z:18120:2:90"),
                List.of("learner:18120:3:100", "learner:18120:3:100"));

        ConsensusMembership membership = transformer.consensusMembershipFrom(raw);

        assertThat(membership.voters()).containsExactlyInAnyOrder(
                new MembershipEndpoint("z:18120:2:90"), new MembershipEndpoint(" host:18120:3:100 "));
        assertThat(membership.learners()).containsExactly(new MembershipEndpoint("learner:18120:3:100"));
        assertThat(transformer.consensusMembershipFrom(new RaftMembership(List.of(), List.of())))
                .isEqualTo(new ConsensusMembership(Set.of(), Set.of()));
    }

    @Test
    void rejectsCrossRoleOverlapAtMappingBoundary() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transformer.consensusMembershipFrom(
                new RaftMembership(List.of("a:18120", "a:18120"), List.of("a:18120"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("overlap");
    }

    @Test
    void restoresDomainAggregateAndProjectsSdkStateWithoutLosingMeaning() {
        BrokerClusterState sdkState = new BrokerClusterState(4L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-a", "10.0.0.1:18020",
                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));

        BrokerCluster aggregate = transformer.brokerClusterFrom(
                sdkState, List.of(), null, 10);
        BrokerClusterState projected = transformer.brokerClusterStateFrom(aggregate);

        assertThat(projected).isEqualTo(sdkState);
    }
}
