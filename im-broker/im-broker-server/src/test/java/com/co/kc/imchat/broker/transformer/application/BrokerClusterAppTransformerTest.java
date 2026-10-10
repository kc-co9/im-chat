package com.co.kc.imchat.broker.transformer.application;

import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerConsensusMembershipResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusStatus;
import com.co.kc.imchat.broker.model.cqrs.result.AddLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult;
import com.co.kc.imchat.broker.model.cqrs.result.PromoteLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.DemoteVoterResult;
import com.co.kc.imchat.broker.model.cqrs.result.RemoveBrokerResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.OperationFailureReason;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.co.kc.imchat.broker.model.cqrs.command.AdvanceClusterOperationCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerClusterAppTransformerTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void routingResultsPreserveHistoricalEpoch(boolean associatedOperation) {
        BrokerClusterOperation operation = associatedOperation ? pendingOperation() : null;
        RoutingEpoch observedEpoch = new RoutingEpoch(7L);
        ClusterOperationId operationId = new ClusterOperationId("join-1");
        BrokerClusterConsensusResult consensus = new BrokerClusterConsensusResult(BrokerClusterConsensusStatus.DUPLICATE, operation, "leader", "retry");
        long expectedEpoch = associatedOperation ? 0L : 7L;
        assertThat(BrokerClusterAppTransformer.INSTANCE.activateBrokerResultFrom(
                operationId, observedEpoch, consensus).acceptedEpoch()).isEqualTo(expectedEpoch);
        assertThat(BrokerClusterAppTransformer.INSTANCE.deactivateBrokerResultFrom(
                operationId, observedEpoch, consensus).acceptedEpoch()).isEqualTo(expectedEpoch);
        assertThat(BrokerClusterAppTransformer.INSTANCE.drainBrokerResultFrom(
                operationId, observedEpoch, consensus).acceptedEpoch()).isEqualTo(expectedEpoch);
        assertThat(BrokerClusterAppTransformer.INSTANCE.forceDeactivateBrokerResultFrom(
                operationId, observedEpoch, consensus).acceptedEpoch()).isEqualTo(expectedEpoch);
    }

    @ParameterizedTest
    @EnumSource(BrokerClusterConsensusStatus.class)
    void consensusMappingPreservesStatusLeaderEndpointAndReason(BrokerClusterConsensusStatus status) {
        ClusterOperationId operationId = new ClusterOperationId("join");
        BrokerClusterConsensusResult consensus = new BrokerClusterConsensusResult(status, null, "127.0.0.1:19001", "processing result");
        AddLearnerResult result = BrokerClusterAppTransformer.INSTANCE.addLearnerResultFrom(operationId, consensus);
        assertThat(result.operationId()).isEqualTo(operationId.value());
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.leaderEndpoint()).isEqualTo(consensus.leaderEndpoint());
        assertThat(result.message()).isEqualTo(consensus.message());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void membershipMappingsPreserveOperationEpochOrUseObservedFallback(boolean associatedOperation) {
        BrokerClusterOperation operation = associatedOperation ? pendingOperation() : null;
        RoutingEpoch observedEpoch = new RoutingEpoch(7L);
        ClusterOperationId operationId = new ClusterOperationId("join-1");
        BrokerClusterConsensusResult consensus = new BrokerClusterConsensusResult(BrokerClusterConsensusStatus.CONFLICT, operation, "127.0.0.1:19001", "conflict");
        long acceptedEpoch = associatedOperation ? 0L : 7L;
        Long targetEpoch = associatedOperation ? 0L : null;
        CompleteLearnerSyncResult sync = BrokerClusterAppTransformer.INSTANCE
                .completeLearnerSyncResultFrom(operationId, observedEpoch, consensus);
        PromoteLearnerResult promotion = BrokerClusterAppTransformer.INSTANCE
                .promoteLearnerResultFrom(operationId, observedEpoch, consensus);
        DemoteVoterResult demotion = BrokerClusterAppTransformer.INSTANCE
                .demoteVoterResultFrom(operationId, observedEpoch, consensus);
        RemoveBrokerResult removal = BrokerClusterAppTransformer.INSTANCE
                .removeBrokerResultFrom(operationId, observedEpoch, consensus);
        assertThat(sync.status()).isEqualTo(BrokerClusterConsensusStatus.CONFLICT);
        assertThat(promotion.status()).isEqualTo("CONFLICT");
        assertThat(demotion.status()).isEqualTo("CONFLICT");
        assertThat(removal.status()).isEqualTo("CONFLICT");
        assertThat(sync.acceptedEpoch()).isEqualTo(acceptedEpoch);
        assertThat(promotion.acceptedEpoch()).isEqualTo(acceptedEpoch);
        assertThat(demotion.acceptedEpoch()).isEqualTo(acceptedEpoch);
        assertThat(removal.acceptedEpoch()).isEqualTo(acceptedEpoch);
        assertThat(sync.targetEpoch()).isEqualTo(targetEpoch);
        assertThat(promotion.targetEpoch()).isEqualTo(targetEpoch);
        assertThat(demotion.targetEpoch()).isEqualTo(targetEpoch);
        assertThat(removal.targetEpoch()).isEqualTo(targetEpoch);
        assertThat(sync.leaderEndpoint()).isEqualTo(consensus.leaderEndpoint());
        assertThat(promotion.leaderEndpoint()).isEqualTo(consensus.leaderEndpoint());
        assertThat(demotion.leaderEndpoint()).isEqualTo(consensus.leaderEndpoint());
        assertThat(removal.leaderEndpoint()).isEqualTo(consensus.leaderEndpoint());
    }

    @Test
    void completionCommandPreservesStagesAndCallerTime() {
        BrokerClusterOperation operation = pendingOperation();
        AdvanceClusterOperationCommand command = BrokerClusterAppTransformer.INSTANCE
                .membershipCompletionCommandFrom(operation, Instant.EPOCH);

        assertThat(command.operationId()).isEqualTo(operation.id());
        assertThat(command.commandDigest()).isEqualTo(operation.commandDigest().value());
        assertThat(command.expectedPhase()).isEqualTo(BrokerClusterOperationPhase.RAFT_COMMITTED);
        assertThat(command.nextPhase()).isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
        assertThat(command.error()).isNull();
        assertThat(command.changedAtEpochMilli()).isZero();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void failureCommandDefaultsReasonWithoutChangingCallerTime(String message) {
        BrokerClusterOperation operation = pendingOperation();
        BrokerConsensusMembershipResult result = new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.REJECTED, null, message);

        AdvanceClusterOperationCommand command = BrokerClusterAppTransformer.INSTANCE
                .membershipFailureCommandFrom(operation, result, Instant.EPOCH);

        assertThat(command.operationId()).isEqualTo(operation.id());
        assertThat(command.commandDigest()).isEqualTo(operation.commandDigest().value());
        assertThat(command.expectedPhase()).isEqualTo(BrokerClusterOperationPhase.PENDING);
        assertThat(command.nextPhase()).isEqualTo(BrokerClusterOperationPhase.FAILED);
        assertThat(command.error()).isEqualTo("membership rejected");
        assertThat(command.changedAtEpochMilli()).isZero();
    }

    @Test
    void failureCommandKeepsGivenReasonAndLaterTime() {
        BrokerConsensusMembershipResult result = new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.REJECTED, null, "peer rejected");

        AdvanceClusterOperationCommand command = BrokerClusterAppTransformer.INSTANCE
                .membershipFailureCommandFrom(pendingOperation(), result, Instant.ofEpochMilli(2_000L));

        assertThat(command.error()).isEqualTo("peer rejected");
        assertThat(command.changedAtEpochMilli()).isEqualTo(2_000L);
    }

    @Test
    void operationUsesMillisAndAllowsCompletionInCreationMillisecond() {
        BrokerClusterOperation operation = pendingOperation();
        assertThat(operation.createdAt()).isEqualTo(Instant.ofEpochMilli(1_000L));
        BrokerClusterOperation completed = operation.advance(BrokerClusterOperationPhase.FAILED,
                new OperationFailureReason("rejected"), Instant.ofEpochSecond(1L, 999_999L));

        assertThat(completed.completedAt()).isEqualTo(operation.createdAt());
        assertThat(JsonUtils.fromJson(JsonUtils.toJson(completed), BrokerClusterOperation.class))
                .isEqualTo(completed);
    }

    @Test
    void jsonReconstructionNormalizesBothTimestampsBeforeComparing() {
        ObjectNode payload = JsonUtils.getMapper().valueToTree(pendingOperation());
        payload.put("createdAt", "1970-01-01T00:00:01.000900Z");
        payload.put("completedAt", "1970-01-01T00:00:01.000100Z");
        payload.put("phase", "FAILED");

        BrokerClusterOperation restored = JsonUtils.fromJson(payload.toString(), BrokerClusterOperation.class);

        assertThat(restored.createdAt()).isEqualTo(Instant.ofEpochMilli(1_000L));
        assertThat(restored.completedAt()).isEqualTo(restored.createdAt());
    }

    @Test
    void earlierCompletionIsNotRepairedAndIsRejectedByOperation() {
        BrokerClusterOperation operation = pendingOperation();
        AdvanceClusterOperationCommand command = BrokerClusterAppTransformer.INSTANCE
                .membershipFailureCommandFrom(operation,
                        new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.REJECTED, null, "rejected"), Instant.ofEpochMilli(999L));

        assertThat(command.changedAtEpochMilli()).isEqualTo(999L);
        assertThatThrownBy(() -> operation.advance(command.nextPhase(),
                new OperationFailureReason(command.error()), Instant.ofEpochMilli(command.changedAtEpochMilli())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operation completion precedes creation");
        assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.PENDING);
    }

    private BrokerClusterOperation pendingOperation() {
        return BrokerClusterOperation.builder().id(new ClusterOperationId("join-1"))
                .commandDigest(new CommandDigest("digest-1"))
                .type(BrokerClusterOperationType.START_LEARNER_SYNC).targetBrokerId(new BrokerId("broker-b"))
                .acceptedRevision(new ClusterRevision(0L)).acceptedEpoch(new RoutingEpoch(0L))
                .resultEpoch(new RoutingEpoch(0L)).phase(BrokerClusterOperationPhase.PENDING)
                .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                .createdAt(Instant.ofEpochSecond(1L, 1L)).build();
    }
}
