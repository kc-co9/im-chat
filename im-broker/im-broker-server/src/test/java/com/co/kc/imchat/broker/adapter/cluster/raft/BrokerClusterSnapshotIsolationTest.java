package com.co.kc.imchat.broker.adapter.cluster.raft;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerJoinIntent;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationProposal;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitBrokerOperationCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.transformer.infrastructure.BrokerClusterRaftTransformer;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerClusterSnapshotIsolationTest {

    @Test
    void snapshotEnvelopeRequiresIdentityAndRejectsPreviousFormat() throws Exception {
        BrokerClusterState initial = new BrokerClusterState(0L, 0L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(new BrokerNode("broker-a", "10.0.0.1:18020",
                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        RaftBrokerClusterStateMachine machine = new RaftBrokerClusterStateMachine("cluster-a", initial);
        BrokerClusterSnapshot snapshot = machine.snapshot();

        assertThat(snapshot.formatVersion()).isEqualTo(6);
        assertThat(snapshot.clusterId()).isEqualTo("cluster-a");
        assertThat(BrokerClusterSnapshotCodec.decode(BrokerClusterSnapshotCodec.encode(snapshot)))
                .isEqualTo(snapshot);
        assertThatThrownBy(() -> new BrokerClusterSnapshot(6, " ", initial,
                List.of(), null, java.util.Map.of())).isInstanceOf(IllegalArgumentException.class);
        ObjectNode oldFormat = (ObjectNode) JsonUtils.getMapper()
                .readTree(BrokerClusterSnapshotCodec.encode(snapshot));
        oldFormat.put("formatVersion", 5);
        assertThatThrownBy(() -> BrokerClusterSnapshotCodec.decode(
                JsonUtils.getMapper().writeValueAsBytes(oldFormat)))
                .isInstanceOf(RuntimeException.class);
        ObjectNode missingIdentity = (ObjectNode) JsonUtils.getMapper()
                .readTree(BrokerClusterSnapshotCodec.encode(snapshot));
        missingIdentity.remove("clusterId");
        assertThatThrownBy(() -> BrokerClusterSnapshotCodec.decode(
                JsonUtils.getMapper().writeValueAsBytes(missingIdentity)))
                .isInstanceOf(RuntimeException.class);

        BrokerClusterSnapshot foreign = new BrokerClusterSnapshot(6, "cluster-b", initial,
                List.of(), null, java.util.Map.of());
        assertThatThrownBy(() -> machine.restore(foreign))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(machine.snapshot()).isEqualTo(snapshot);
    }

    @Test
    void pendingBrokerAdditionRestoresItsEndpointAndCommitsWithoutTargetTopology() {
        BrokerClusterState initial = new BrokerClusterState(0L, 0L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(new BrokerNode("broker-a", "10.0.0.1:18020",
                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        RaftBrokerClusterStateMachine first = new RaftBrokerClusterStateMachine("cluster-a", initial);
        BrokerClusterOperation operation = first.apply(new BeginBrokerClusterOperationCommand(
                BrokerClusterOperationProposal.builder().operationId(new ClusterOperationId("add-1"))
                        .commandDigest(new CommandDigest("add-digest"))
                        .expectedRevision(first.getCluster().revision())
                        .operationType(BrokerClusterOperationType.ADD_BROKER)
                        .targetBrokerId(new BrokerId("broker-b"))
                        .routeEndpoint(new BrokerRouteEndpoint("10.0.0.2:18020"))
                        .changedAt(Instant.ofEpochMilli(1_000L)).build())).operation();
        assertThat(first.getCluster().findNode(operation.targetBrokerId())).isEmpty();
        RaftBrokerClusterStateMachine restored = new RaftBrokerClusterStateMachine("cluster-a", initial);
        restored.restore(BrokerClusterSnapshotCodec.decode(BrokerClusterSnapshotCodec.encode(first.snapshot())));

        assertThat(restored.apply(new CommitBrokerOperationCommand(operation.id(), operation.commandDigest().value(),
                operation.acceptedRevision(), 1_100L)).accepted()).isTrue();
        assertThat(restored.getCluster().findNode(operation.targetBrokerId()).orElseThrow().routeEndpoint())
                .isEqualTo(operation.routeEndpoint());
        assertThat(restored.getCluster().findNode(operation.targetBrokerId()).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.DISCOVERED);
    }

    @Test
    void restoredSnapshotKeepsCommittedNodesAndOperationFactsIsolated() {
        BrokerClusterState initial = new BrokerClusterState(0L, 0L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(
                new BrokerNode("broker-a", "10.0.0.1:18020",
                        com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER,
                        BrokerRoutingState.ACTIVE),
                new BrokerNode("broker-b", "10.0.0.2:18020",
                        com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.DISCOVERED,
                        BrokerRoutingState.INACTIVE)));
        RaftBrokerClusterStateMachine first = new RaftBrokerClusterStateMachine("cluster-a", initial);
        BrokerCluster cluster = first.getCluster();
        BrokerId targetId = new BrokerId("broker-b");
        BrokerClusterOperation operation = cluster.addLearner(LearnerJoinIntent.builder()
                .operationId(new ClusterOperationId("join-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(cluster.revision()).brokerId(targetId)
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build()).operation();
        assertThat(first.apply(new BeginBrokerClusterOperationCommand(
                BrokerClusterRaftTransformer.INSTANCE.proposalFrom(operation))).accepted()).isTrue();
        assertThat(first.apply(new CommitBrokerOperationCommand(operation.id(), operation.commandDigest().value(),
                operation.acceptedRevision(), 1_100L)).accepted()).isTrue();

        BrokerClusterSnapshot saved = BrokerClusterSnapshotCodec.decode(
                BrokerClusterSnapshotCodec.encode(first.snapshot()));
        ObjectNode operationJson = JsonUtils.getMapper().valueToTree(saved.operations().getFirst());
        assertThat(operationJson.has("resultEpoch")).isTrue();
        assertThat(operationJson.has("targetEpoch")).isFalse();
        RaftBrokerClusterStateMachine restored = new RaftBrokerClusterStateMachine("cluster-a", initial);
        restored.restore(saved);
        restored.getCluster().findNode(targetId).orElseThrow().completeLearnerSync();

        assertThat(restored.getCluster().findNode(targetId).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.LEARNER_SYNCING);
        assertThat(restored.findOperation(operation.id())).contains(saved.operations().getFirst());
        assertThat(restored.committedState()).isEqualTo(first.committedState());
    }
}
