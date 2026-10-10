package com.co.kc.imchat.broker.domain.cluster.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.co.kc.imchat.common.utils.JsonUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerChangeIntentTest {

    @ParameterizedTest
    @EnumSource(BrokerClusterOperationType.class)
    void onlyJoinAndRemovalAllowFailureOnDefinitiveMembershipRejection(BrokerClusterOperationType type) {
        BrokerClusterOperation operation = BrokerClusterOperation.builder()
                .id(new ClusterOperationId("operation-1"))
                .commandDigest(new CommandDigest("digest-1")).type(type)
                .targetBrokerId(new BrokerId("broker-b"))
                .acceptedRevision(new ClusterRevision(0L)).acceptedEpoch(new RoutingEpoch(0L))
                .resultEpoch(new RoutingEpoch(0L)).phase(BrokerClusterOperationPhase.PENDING)
                .createdAt(Instant.ofEpochMilli(1_000L))
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .routeEndpoint(new BrokerRouteEndpoint("10.0.0.2:18020"))
                .minimumVoterCount(new MinimumVoterCount(1))
                .drainProgress(type.routeRecovery()
                        ? new DrainProgress(List.of(), Instant.ofEpochMilli(2_000L), null) : null).build();

        boolean expected = type == BrokerClusterOperationType.ADD_LEARNER
                || type == BrokerClusterOperationType.START_LEARNER_SYNC
                || type == BrokerClusterOperationType.REMOVE_BROKER;
        assertThat(operation.canFailOnRejection()).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(BrokerClusterOperationPhase.class)
    void routeRecoveryBlocksNewOperationsUntilTerminal(BrokerClusterOperationPhase phase) {
        assertThat(operation(BrokerClusterOperationType.DRAIN_BROKER, phase, true).blocksNewOperation())
                .isEqualTo(!phase.terminal());
    }

    @ParameterizedTest
    @EnumSource(BrokerClusterOperationPhase.class)
    void nonRoutingOperationBlocksOnlyItsUncommittedOrAuthorizingPhases(BrokerClusterOperationPhase phase) {
        boolean expected = phase == BrokerClusterOperationPhase.PENDING
                || phase == BrokerClusterOperationPhase.FENCING_OLD_EPOCH
                || phase == BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH;
        assertThat(operation(BrokerClusterOperationType.ADD_BROKER, phase, false).blocksNewOperation())
                .isEqualTo(expected);
    }

    @Test
    void committedRoutingChangeStillBlocksUntilNewEpochIsAuthorized() {
        assertThat(operation(BrokerClusterOperationType.ACTIVATE_BROKER,
                BrokerClusterOperationPhase.RAFT_COMMITTED, true).blocksNewOperation()).isTrue();
        assertThat(operation(BrokerClusterOperationType.ADD_BROKER,
                BrokerClusterOperationPhase.RAFT_COMMITTED, false).blocksNewOperation()).isFalse();
    }

    private BrokerClusterOperation operation(BrokerClusterOperationType type,
                                             BrokerClusterOperationPhase phase, boolean changesEpoch) {
        return BrokerClusterOperation.builder().id(new ClusterOperationId("operation-1"))
                .commandDigest(new CommandDigest("digest-1")).type(type)
                .targetBrokerId(new BrokerId("broker-b"))
                .acceptedRevision(new ClusterRevision(0L)).acceptedEpoch(new RoutingEpoch(0L))
                .resultEpoch(new RoutingEpoch(changesEpoch ? 1L : 0L)).phase(phase)
                .createdAt(Instant.ofEpochMilli(1_000L))
                .routeEndpoint(type == BrokerClusterOperationType.ADD_BROKER
                        ? new BrokerRouteEndpoint("10.0.0.2:18020") : null)
                .drainProgress(type.routeRecovery()
                        ? new DrainProgress(List.of(), Instant.ofEpochMilli(2_000L), null) : null).build();
    }

    @ParameterizedTest
    @EnumSource(value = BrokerClusterOperationType.class,
            names = {"START_LEARNER_SYNC", "ADD_LEARNER", "PROMOTE_LEARNER"})
    void proposalOwnsLearnerJoinClassificationWithoutAddingSerializedField(BrokerClusterOperationType type) {
        BrokerClusterOperationProposal proposal = BrokerClusterOperationProposal.builder()
                .operationId(new ClusterOperationId("operation-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(new ClusterRevision(0L)).operationType(type)
                .targetBrokerId(new BrokerId("broker-b"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build();

        assertThat(proposal.learnerJoin()).isEqualTo(type != BrokerClusterOperationType.PROMOTE_LEARNER);
        assertThat(JsonUtils.toJson(proposal)).doesNotContain("learnerJoin");
    }

    @Test
    void drainAndFailureRemovalRequireTheirFrozenEvidence() {
        assertThatThrownBy(() -> BrokerDrainIntent.builder()
                .operationId(new ClusterOperationId("drain-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(new ClusterRevision(0L))
                .brokerId(new BrokerId("broker-b"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drain intent is incomplete");
        assertThatThrownBy(() -> BrokerFailureRemovalIntent.builder()
                .operationId(new ClusterOperationId("fail-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(new ClusterRevision(0L))
                .brokerId(new BrokerId("broker-b"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("removeFailedBroker intent is incomplete");
    }

    @Test
    void demotionAndRemovalRequireTheirMinimumVoterCount() {
        assertThatThrownBy(() -> VoterDemotionIntent.builder()
                .operationId(new ClusterOperationId("demote-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(new ClusterRevision(0L))
                .brokerId(new BrokerId("broker-b"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("demoteVoter intent is incomplete");
        assertThatThrownBy(() -> BrokerRemovalIntent.builder()
                .operationId(new ClusterOperationId("remove-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(new ClusterRevision(0L))
                .brokerId(new BrokerId("broker-b"))
                .changedAt(Instant.ofEpochMilli(1_000L)).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("removeBroker intent is incomplete");
    }

    @Test
    void learnerJoinRequiresItsMembershipEndpoint() {
        assertThatThrownBy(() -> LearnerJoinIntent.builder()
                .operationId(new ClusterOperationId("join-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(new ClusterRevision(0L))
                .brokerId(new BrokerId("broker-b"))
                .changedAt(Instant.ofEpochMilli(1_000L))
                .build()).isInstanceOf(IllegalStateException.class)
                .hasMessage("addLearner intent is incomplete");
    }

    @Test
    void operationOwnsRepeatedIntentAssessment() {
        BrokerClusterOperation operation = BrokerClusterOperation.builder()
                .id(new ClusterOperationId("join-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .type(BrokerClusterOperationType.START_LEARNER_SYNC)
                .targetBrokerId(new BrokerId("broker-b"))
                .acceptedEpoch(new RoutingEpoch(0L))
                .resultEpoch(new RoutingEpoch(0L))
                .acceptedRevision(new ClusterRevision(0L))
                .phase(BrokerClusterOperationPhase.PENDING)
                .createdAt(Instant.ofEpochMilli(1_000L))
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .build();

        LearnerJoinIntent repeated = LearnerJoinIntent.builder()
                .operationId(operation.id()).commandDigest(operation.commandDigest())
                .brokerId(operation.targetBrokerId()).expectedRevision(new ClusterRevision(9L))
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .changedAt(Instant.ofEpochMilli(2_000L)).build();
        BrokerActivationIntent differentType = BrokerActivationIntent.builder()
                .operationId(operation.id()).commandDigest(operation.commandDigest())
                .brokerId(operation.targetBrokerId()).expectedRevision(operation.acceptedRevision())
                .changedAt(Instant.ofEpochMilli(2_000L)).build();

        assertThat(operation.checkRepeatedIntent(repeated).status())
                .isEqualTo(BrokerClusterDecision.Status.DUPLICATE);
        assertThat(operation.checkRepeatedIntent(differentType).status())
                .isEqualTo(BrokerClusterDecision.Status.REJECTED);
        LearnerJoinIntent differentDigest = LearnerJoinIntent.builder()
                .operationId(operation.id()).commandDigest(new CommandDigest("different-digest"))
                .brokerId(operation.targetBrokerId()).expectedRevision(operation.acceptedRevision())
                .membershipEndpoint(repeated.membershipEndpoint()).changedAt(repeated.changedAt()).build();
        LearnerJoinIntent differentBroker = LearnerJoinIntent.builder()
                .operationId(operation.id()).commandDigest(operation.commandDigest())
                .brokerId(new BrokerId("broker-c")).expectedRevision(operation.acceptedRevision())
                .membershipEndpoint(repeated.membershipEndpoint()).changedAt(repeated.changedAt()).build();
        assertThat(operation.checkRepeatedIntent(differentDigest).status())
                .isEqualTo(BrokerClusterDecision.Status.REJECTED);
        assertThat(operation.checkRepeatedIntent(differentBroker).status())
                .isEqualTo(BrokerClusterDecision.Status.REJECTED);
        BrokerClusterOperation failed = operation.advance(BrokerClusterOperationPhase.FAILED,
                new OperationFailureReason("membership rejected"), Instant.ofEpochMilli(2_000L));
        assertThat(failed.checkRepeatedIntent(repeated).status())
                .isEqualTo(BrokerClusterDecision.Status.REJECTED);
    }
}
