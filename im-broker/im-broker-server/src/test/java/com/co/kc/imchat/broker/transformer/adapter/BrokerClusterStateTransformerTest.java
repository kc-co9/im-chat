package com.co.kc.imchat.broker.transformer.adapter;

import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipStep;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
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

    @Test
    void restoresStepsPermitsDigestAndIncarnationWithoutChangingSdkTopology() {
        ClusterOperationId id =
                new ClusterOperationId("join");
        ConsensusMembership target = new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120")),
                Set.of(new MembershipEndpoint("b:18120")));
        BrokerMembershipPermit permit =
                new BrokerMembershipPermit("permit", id, "final-target", target,
                        new BrokerId("a"), "original-process",
                        BrokerMembershipPermit.Result.UNKNOWN);
        BrokerMembershipStep step =
                new BrokerMembershipStep("final-target", target, List.of(permit));
        BrokerClusterOperation operation =
                BrokerClusterOperation.builder().id(id)
                        .commandDigest(new CommandDigest("digest"))
                        .type(BrokerClusterOperationType.START_LEARNER_SYNC)
                        .targetBrokerId(new BrokerId("b"))
                        .acceptedRevision(new ClusterRevision(0L))
                        .acceptedEpoch(new RoutingEpoch(0L))
                        .resultEpoch(new RoutingEpoch(0L))
                        .phase(BrokerClusterOperationPhase.PENDING)
                        .membershipEndpoint(new MembershipEndpoint("b:18120")).createdAt(java.time.Instant.EPOCH)
                        .membershipSteps(List.of(step)).build();
        BrokerClusterState state = new BrokerClusterState(2L, 0L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("a", "a:18020", BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE),
                        new BrokerNode("b", "b:18020", BrokerMembershipRole.DISCOVERED, BrokerRoutingState.INACTIVE)));
        BrokerCluster restored = transformer.brokerClusterFrom(state, List.of(operation), null, 100,
                java.util.Map.of("a", "a:18120", "b", "b:18120"));
        assertThat(restored.findOperation(id).orElseThrow().membershipSteps()).containsExactly(step);
        assertThat(restored.findOperation(id).orElseThrow().currentMembershipStep().currentPermit()).isEqualTo(permit);
        assertThat(restored.findOperation(id).orElseThrow().currentMembershipStep().targetDigest()).isEqualTo(step.targetDigest());
        assertThat(transformer.brokerClusterStateFrom(restored)).isEqualTo(state);
        assertThat(permit.drained()).isFalse();
    }
}
