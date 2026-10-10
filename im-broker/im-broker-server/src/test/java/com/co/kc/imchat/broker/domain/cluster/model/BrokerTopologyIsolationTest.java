package com.co.kc.imchat.broker.domain.cluster.model;

import org.junit.jupiter.api.Test;
import com.co.kc.imchat.common.utils.JsonUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerTopologyIsolationTest {
    private final BrokerId targetId = new BrokerId("broker-b");

    @Test
    void invalidCompletionTimeReturnsRejectionWithoutChangingCommittedOperation() {
        BrokerCluster cluster = cluster(discovered());
        BrokerClusterOperation pending = cluster.addLearner(joinIntent()).operation();
        cluster.commitOperation(pending.id(), pending.commandDigest(), pending.acceptedRevision(),
                Instant.ofEpochMilli(1_100L));
        BrokerClusterOperation committed = cluster.findOperation(pending.id()).orElseThrow();

        BrokerClusterDecision decision = cluster.advanceOperation(committed.id(), committed.commandDigest(),
                BrokerClusterOperationPhase.RAFT_COMMITTED, BrokerClusterOperationPhase.SUCCEEDED,
                null, Instant.ofEpochMilli(999L));

        assertThat(decision.status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(decision.reason().value()).isEqualTo("operation completion precedes creation");
        assertThat(cluster.findOperation(committed.id())).contains(committed);
    }

    @Test
    void beginRevisionOverflowDoesNotRegisterOperationOrEndpoint() {
        BrokerCluster cluster = BrokerCluster.builder()
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .revision(new ClusterRevision(Long.MAX_VALUE)).epoch(new RoutingEpoch(0L))
                .brokers(List.of(active(), discovered())).operations(List.of())
                .operationHistoryCapacity(new OperationHistoryCapacity(20)).build();
        LearnerJoinIntent intent = LearnerJoinIntent.builder()
                .operationId(new ClusterOperationId("join-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(cluster.revision()).brokerId(targetId)
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build();

        assertThatThrownBy(() -> cluster.addLearner(intent)).isInstanceOf(ArithmeticException.class);
        assertThat(cluster.operations()).isEmpty();
        assertThat(cluster.membershipEndpoints()).isEmpty();
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(Long.MAX_VALUE));
        assertThat(cluster.findNode(targetId).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.DISCOVERED);
    }

    @Test
    void commitRejectsIncompatibleRestoredStateWithoutChangingNodeOrOperation() {
        BrokerClusterOperation pending = cluster(discovered()).addLearner(joinIntent()).operation();
        BrokerCluster restored = BrokerCluster.builder()
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .revision(pending.acceptedRevision().next()).epoch(pending.acceptedEpoch())
                .brokers(List.of(active(), new BrokerNode(targetId, discovered().routeEndpoint(),
                        BrokerMembershipRole.VOTER, BrokerTrafficState.INACTIVE)))
                .operations(List.of(pending)).operationHistoryCapacity(new OperationHistoryCapacity(20)).build();
        ClusterRevision revision = restored.revision();

        assertThat(restored.commitOperation(pending.id(), pending.commandDigest(), pending.acceptedRevision(),
                Instant.ofEpochMilli(1_100L)).status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(restored.findNode(targetId).orElseThrow().membershipRole()).isEqualTo(BrokerMembershipRole.VOTER);
        assertThat(restored.findOperation(pending.id())).contains(pending);
        assertThat(restored.revision()).isEqualTo(revision);
    }

    @Test
    void revisionOverflowDoesNotPartiallyApplyCommit() {
        BrokerClusterOperation pending = BrokerClusterOperation.builder()
                .id(new ClusterOperationId("join-1")).commandDigest(new CommandDigest("digest-1"))
                .type(BrokerClusterOperationType.START_LEARNER_SYNC).targetBrokerId(targetId)
                .membershipEndpoint(joinIntent().membershipEndpoint())
                .acceptedEpoch(new RoutingEpoch(0L)).resultEpoch(new RoutingEpoch(0L))
                .acceptedRevision(new ClusterRevision(Long.MAX_VALUE - 1L))
                .phase(BrokerClusterOperationPhase.PENDING).createdAt(Instant.ofEpochMilli(1_000L)).build();
        BrokerCluster restored = BrokerCluster.builder()
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .revision(new ClusterRevision(Long.MAX_VALUE)).epoch(pending.acceptedEpoch())
                .brokers(List.of(active(), discovered())).operations(List.of(pending))
                .operationHistoryCapacity(new OperationHistoryCapacity(20)).build();

        assertThat(restored.commitOperation(pending.id(), pending.commandDigest(), pending.acceptedRevision(),
                Instant.ofEpochMilli(1_100L)).status()).isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(restored.findNode(targetId).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.DISCOVERED);
        assertThat(restored.findOperation(pending.id())).contains(pending);
        assertThat(restored.revision()).isEqualTo(new ClusterRevision(Long.MAX_VALUE));
    }

    @Test
    void builderAndReadMethodsDoNotExposeOwnedNodes() {
        BrokerNode supplied = discovered();
        BrokerCluster cluster = cluster(supplied);
        supplied.joinAsLearner();

        BrokerNode found = cluster.findNode(targetId).orElseThrow();
        found.joinAsLearner();
        BrokerNode listed = cluster.brokers().stream().filter(node -> node.id().equals(targetId))
                .findFirst().orElseThrow();
        listed.joinAsLearner();

        assertThat(supplied.membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER_SYNCING);
        assertThat(found).isNotSameAs(listed);
        assertThat(cluster.findNode(targetId).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.DISCOVERED);
        assertThat(cluster.revision()).isEqualTo(new ClusterRevision(0L));
    }

    @Test
    void proposalAndOperationContainFactsNotMutableTopology() {
        BrokerClusterOperationProposal proposal = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("join-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(new ClusterRevision(0L))
                .operationType(BrokerClusterOperationType.START_LEARNER_SYNC)
                .targetBrokerId(targetId).membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build();
        BrokerClusterOperation operation = cluster(discovered()).submitOperation(proposal).operation();

        assertThat(JsonUtils.toJson(proposal)).doesNotContain("\"brokers\"");
        assertThat(JsonUtils.toJson(operation)).doesNotContain("targetBrokers");
        assertThat(operation.membershipEndpoint()).isEqualTo(proposal.membershipEndpoint());
    }

    @Test
    void beginDoesNotChangeNodeAndCommitAppliesItsRegisteredBehaviorOnce() {
        BrokerCluster cluster = cluster(discovered());
        BrokerCluster before = cluster.snapshot();
        BrokerClusterOperation operation = cluster.addLearner(joinIntent()).operation();
        assertThat(cluster.findNode(targetId).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.DISCOVERED);

        assertThat(cluster.commitOperation(operation.id(), operation.commandDigest(), operation.acceptedRevision(),
                Instant.ofEpochMilli(1_100L)).status()).isEqualTo(BrokerClusterDecision.Status.APPLIED);
        ClusterRevision committedRevision = cluster.revision();
        assertThat(cluster.commitOperation(operation.id(), operation.commandDigest(), operation.acceptedRevision(),
                Instant.ofEpochMilli(1_200L)).status()).isEqualTo(BrokerClusterDecision.Status.DUPLICATE);
        assertThat(cluster.revision()).isEqualTo(committedRevision);
        assertThat(cluster.findNode(targetId).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.LEARNER_SYNCING);
        assertThat(before.findNode(targetId).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.DISCOVERED);
        assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.PENDING);
        assertThat(cluster.findOperation(operation.id()).orElseThrow().phase())
                .isEqualTo(BrokerClusterOperationPhase.RAFT_COMMITTED);
    }

    @Test
    void snapshotCanCommitItsOwnChangeWithoutChangingOriginalAggregate() {
        BrokerCluster original = cluster(discovered());
        BrokerCluster snapshot = original.snapshot();
        BrokerClusterOperation operation = snapshot.addLearner(joinIntent()).operation();

        assertThat(snapshot.commitOperation(operation.id(), operation.commandDigest(), operation.acceptedRevision(),
                Instant.ofEpochMilli(1_100L)).accepted()).isTrue();

        assertThat(snapshot.findNode(targetId).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.LEARNER_SYNCING);
        assertThat(original.findNode(targetId).orElseThrow().membershipRole())
                .isEqualTo(BrokerMembershipRole.DISCOVERED);
        assertThat(original.operations()).isEmpty();
    }

    @Test
    void laterNodeChangesDoNotRewriteEarlierOperationFacts() {
        BrokerCluster cluster = cluster(discovered());
        BrokerClusterOperation joined = cluster.addLearner(joinIntent()).operation();
        cluster.commitOperation(joined.id(), joined.commandDigest(), joined.acceptedRevision(),
                Instant.ofEpochMilli(1_100L));
        BrokerClusterOperation history = cluster.findOperation(joined.id()).orElseThrow();
        BrokerClusterOperation synced = cluster.confirmLearner(LearnerSyncCompletionIntent.builder()
                .operationId(new ClusterOperationId("sync-1")).commandDigest(new CommandDigest("sync-digest"))
                .expectedRevision(cluster.revision()).brokerId(targetId)
                .changedAt(Instant.ofEpochMilli(1_200L)).build()).operation();
        assertThat(cluster.commitOperation(synced.id(), synced.commandDigest(), synced.acceptedRevision(),
                Instant.ofEpochMilli(1_300L)).accepted()).isTrue();

        assertThat(cluster.findNode(targetId).orElseThrow().membershipRole()).isEqualTo(BrokerMembershipRole.LEARNER);
        assertThat(cluster.findOperation(joined.id())).contains(history);
        assertThat(history.type()).isEqualTo(BrokerClusterOperationType.START_LEARNER_SYNC);
        assertThat(history.membershipEndpoint()).isEqualTo(joinIntent().membershipEndpoint());
    }

    private LearnerJoinIntent joinIntent() {
        return LearnerJoinIntent.builder().operationId(new ClusterOperationId("join-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(new ClusterRevision(0L)).brokerId(targetId)
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build();
    }

    private BrokerCluster cluster(BrokerNode target) {
        return BrokerCluster.builder()
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .revision(new ClusterRevision(0L)).epoch(new RoutingEpoch(0L))
                .brokers(List.of(active(), target)).operations(List.of())
                .operationHistoryCapacity(new OperationHistoryCapacity(20)).build();
    }

    private BrokerNode active() {
        return new BrokerNode(new BrokerId("broker-a"), new BrokerRouteEndpoint("10.0.0.1:18020"),
                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE);
    }

    private BrokerNode discovered() {
        return BrokerNode.discovered(targetId, new BrokerRouteEndpoint("10.0.0.2:18020"));
    }
}
