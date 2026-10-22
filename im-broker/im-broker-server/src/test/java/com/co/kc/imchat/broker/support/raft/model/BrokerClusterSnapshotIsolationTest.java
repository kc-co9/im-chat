package com.co.kc.imchat.broker.support.raft.model;

import java.util.Map;
import com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.plugin.raft.spi.RaftStateHandler;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerJoinIntent;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationProposal;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitBrokerOperationCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerClusterSnapshotIsolationTest {

    @Test
    void stateHandlerAppliesTypedCommandAndRestoresIdenticalSnapshot() {
        BrokerClusterState initial = new BrokerClusterState(0L, 0L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(new BrokerNode("broker-a", "10.0.0.1:18020",
                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        RaftStateHandler<BrokerClusterConsensusCommand, BrokerClusterConsensusResult>
                handler = new RaftBrokerClusterStateMachine("cluster-a", initial, 1000, Map.of());
        BrokerClusterOperationProposal proposal = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("add-1")).commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(new ClusterRevision(0L))
                .operationType(BrokerClusterOperationType.ADD_BROKER).targetBrokerId(new BrokerId("broker-b"))
                .routeEndpoint(new BrokerRouteEndpoint("10.0.0.2:18020"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build();
        assertThat(handler.apply(new BeginBrokerClusterOperationCommand(proposal))
                .accepted()).isTrue();
        byte[] encoded = handler.snapshot();
        RaftBrokerClusterStateMachine restored = new RaftBrokerClusterStateMachine("cluster-a", initial, 1000, Map.of());
        restored.restore(encoded);
        assertThat(restored.findOperation(proposal.operationId())).isPresent();
        assertThat(restored.snapshot()).isEqualTo(encoded);
    }


    @Test
    void snapshotEnvelopeRequiresIdentityAndRejectsPreviousFormat() throws Exception {
        BrokerClusterState initial = new BrokerClusterState(0L, 0L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(new BrokerNode("broker-a", "10.0.0.1:18020",
                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        RaftBrokerClusterStateMachine machine = new RaftBrokerClusterStateMachine("cluster-a", initial, 1000, Map.of());
        BrokerClusterSnapshot snapshot = machine.snapshotState();

        assertThat(snapshot.formatVersion()).isEqualTo(2);
        assertThat(snapshot.clusterId()).isEqualTo("cluster-a");
        assertThat(BrokerClusterSnapshot.decode(BrokerClusterSnapshot.encode(snapshot)))
                .isEqualTo(snapshot);
        assertThatThrownBy(() -> new BrokerClusterSnapshot(2, " ", initial,
                List.of(), null, java.util.Map.of())).isInstanceOf(IllegalArgumentException.class);
        ObjectNode oldFormat = (ObjectNode) JsonUtils.getMapper()
                .readTree(BrokerClusterSnapshot.encode(snapshot));
        oldFormat.put("formatVersion", 1);
        byte[] oldBytes = JsonUtils.getMapper().writeValueAsBytes(oldFormat);
        assertThatThrownBy(() -> BrokerClusterSnapshot.decode(oldBytes))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> machine.restore(oldBytes)).isInstanceOf(RuntimeException.class);
        assertThat(machine.snapshotState()).isEqualTo(snapshot);
        assertThatThrownBy(() -> machine.restore(new byte[] {0, 1, 2})).isInstanceOf(RuntimeException.class);
        assertThat(machine.snapshotState()).isEqualTo(snapshot);
        ObjectNode missingIdentity = (ObjectNode) JsonUtils.getMapper()
                .readTree(BrokerClusterSnapshot.encode(snapshot));
        missingIdentity.remove("clusterId");
        assertThatThrownBy(() -> BrokerClusterSnapshot.decode(
                JsonUtils.getMapper().writeValueAsBytes(missingIdentity)))
                .isInstanceOf(RuntimeException.class);

        BrokerClusterSnapshot foreign = new BrokerClusterSnapshot(2, "cluster-b", initial,
                List.of(), null, java.util.Map.of());
        assertThatThrownBy(() -> machine.restore(foreign))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(machine.snapshotState()).isEqualTo(snapshot);
    }

    @Test
    void pendingBrokerAdditionRestoresItsEndpointAndCommitsWithoutTargetTopology() {
        BrokerClusterState initial = new BrokerClusterState(0L, 0L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(new BrokerNode("broker-a", "10.0.0.1:18020",
                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        RaftBrokerClusterStateMachine first = new RaftBrokerClusterStateMachine("cluster-a", initial, 1000, Map.of());
        BrokerClusterOperation operation = first.apply(new BeginBrokerClusterOperationCommand(
                BrokerClusterOperationProposal.builder().operationId(new ClusterOperationId("add-1"))
                        .commandDigest(new CommandDigest("add-digest"))
                        .expectedRevision(first.getCluster().revision())
                        .operationType(BrokerClusterOperationType.ADD_BROKER)
                        .targetBrokerId(new BrokerId("broker-b"))
                        .routeEndpoint(new BrokerRouteEndpoint("10.0.0.2:18020"))
                        .changedAt(Instant.ofEpochMilli(1_000L)).build())).operation();
        assertThat(first.getCluster().findNode(operation.targetBrokerId())).isEmpty();
        RaftBrokerClusterStateMachine restored = new RaftBrokerClusterStateMachine("cluster-a", initial, 1000, Map.of());
        restored.restore(BrokerClusterSnapshot.decode(BrokerClusterSnapshot.encode(first.snapshotState())));

        assertThat(restored.apply(new CommitBrokerOperationCommand(operation.id(), operation.commandDigest().value(),
                operation.acceptedRevision(), Instant.ofEpochMilli(1_100L))).accepted()).isTrue();
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
        RaftBrokerClusterStateMachine first = new RaftBrokerClusterStateMachine("cluster-a", initial, 1000, Map.of());
        BrokerCluster cluster = first.getCluster();
        BrokerId targetId = new BrokerId("broker-b");
        BrokerClusterOperation operation = cluster.addLearner(LearnerJoinIntent.builder()
                .operationId(new ClusterOperationId("join-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(cluster.revision()).brokerId(targetId)
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build()).operation();
        assertThat(first.apply(BrokerClusterRaftTransformer.INSTANCE.beginCommandFrom(operation)).accepted()).isTrue();
        assertThat(first.apply(new CommitBrokerOperationCommand(operation.id(), operation.commandDigest().value(),
                operation.acceptedRevision(), Instant.ofEpochMilli(1_100L))).accepted()).isTrue();

        BrokerClusterSnapshot saved = BrokerClusterSnapshot.decode(
                BrokerClusterSnapshot.encode(first.snapshotState()));
        ObjectNode operationJson = JsonUtils.getMapper().valueToTree(saved.operations().getFirst());
        assertThat(operationJson.has("resultEpoch")).isTrue();
        assertThat(operationJson.has("targetEpoch")).isFalse();
        RaftBrokerClusterStateMachine restored = new RaftBrokerClusterStateMachine("cluster-a", initial, 1000, Map.of());
        restored.restore(saved);
        restored.getCluster().findNode(targetId).orElseThrow().completeLearnerSync();

        assertThat(restored.getCluster().findNode(targetId).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.LEARNER_SYNCING);
        assertThat(restored.findOperation(operation.id())).contains(saved.operations().getFirst());
        assertThat(restored.committedState()).isEqualTo(first.committedState());
    }
}
