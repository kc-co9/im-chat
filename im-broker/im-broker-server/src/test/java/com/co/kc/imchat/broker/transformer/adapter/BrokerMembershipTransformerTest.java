package com.co.kc.imchat.broker.transformer.adapter;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationOutcome;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipExecutionConfirmation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationAttempt;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmMembershipPermitDrainedCommand;
import com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerMembershipTransformerTest {
    private final BrokerMembershipTransformer transformer = BrokerMembershipTransformer.INSTANCE;

    @ParameterizedTest
    @EnumSource(com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult.Status.class)
    void nativeObservationPreservesStatusCodeAndDiagnostics(
            com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult.Status status) {
        com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult result =
                transformer.membershipConfigurationResultFrom(new com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult(
                        status, 37, "leader", "reason"));
        assertThat(result.status().name()).isEqualTo(status.name());
        assertThat(result.nativeCode()).isEqualTo(37);
        assertThat(result.leaderEndpoint()).isEqualTo("leader");
        assertThat(result.message()).isEqualTo("reason");
    }

    @ParameterizedTest
    @EnumSource(com.co.kc.imchat.broker.adapter.cluster.membership.MembershipExecutionAdmissionResult.Status.class)
    void localAdmissionCanMapOnlyRejections(
            com.co.kc.imchat.broker.adapter.cluster.membership.MembershipExecutionAdmissionResult.Status status) {
        com.co.kc.imchat.broker.adapter.cluster.membership.MembershipExecutionAdmissionResult admission =
                new com.co.kc.imchat.broker.adapter.cluster.membership.MembershipExecutionAdmissionResult(status, "leader", "reason");
        if (status == com.co.kc.imchat.broker.adapter.cluster.membership.MembershipExecutionAdmissionResult.Status.ACCEPTED) {
            assertThatThrownBy(() -> transformer.membershipAdmissionOutcomeFrom(admission))
                    .isInstanceOf(IllegalArgumentException.class);
            return;
        }
        MembershipOperationOutcome outcome = transformer.membershipAdmissionOutcomeFrom(admission);
        assertThat(outcome.status().name()).isEqualTo(status.name());
        assertThat(outcome.operation()).isNull();
        assertThat(outcome.leaderEndpoint()).isEqualTo("leader");
        assertThat(outcome.message()).isEqualTo("reason");
    }

    @ParameterizedTest
    @EnumSource(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.class)
    void confirmationResponseRoundTripPreservesEveryStatus(
            com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status status) {
        com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult result =
                new com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult(status, "leader", "reason");
        assertThat(transformer.membershipConfirmationResultFrom(transformer.membershipPermitDrainedResponseFrom(result)))
                .isEqualTo(result);
    }

    @Test
    void reportMappingPreservesAuthenticatedEnvelopeForDeduplication() {
        AcceptMembershipPermitDrainedCommand request = new AcceptMembershipPermitDrainedCommand(
                "cluster", "caller", "receiver", "operation", "step", "permit", "executor", "incarnation",
                "digest", AcceptMembershipPermitDrainedCommand.Result.TARGET_APPLIED, "authenticated");
        com.co.kc.imchat.broker.domain.cluster.model.MembershipExecutionReport report =
                transformer.membershipExecutionReportFrom(request);
        assertThat(report).isEqualTo(new com.co.kc.imchat.broker.domain.cluster.model.MembershipExecutionReport(
                new com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId("cluster"), new BrokerId("caller"),
                new BrokerId("receiver"), new ClusterOperationId("operation"), "step", "permit", new BrokerId("executor"),
                "incarnation", "digest", BrokerMembershipPermit.Result.TARGET_APPLIED, new BrokerId("authenticated")));
    }

    @ParameterizedTest
    @EnumSource(BrokerClusterConsensusResult.Status.class)
    void memberOutcomeRoundTripPreservesEveryStatusAndNullableFields(BrokerClusterConsensusResult.Status status) {
        BrokerClusterConsensusResult result = new BrokerClusterConsensusResult(status, null, "leader-hint", "reason");
        MembershipOperationOutcome outcome = transformer.membershipOperationOutcomeFrom(result);
        assertThat(outcome.status().name()).isEqualTo(status.name());
        assertThat(outcome.leaderEndpoint()).isEqualTo("leader-hint");
        assertThat(outcome.message()).isEqualTo("reason");
        assertThat(outcome.operation()).isNull();
        assertThat(transformer.membershipConsensusResultFrom(outcome)).isEqualTo(result);
        BrokerClusterConsensusResult empty = new BrokerClusterConsensusResult(status, null, null, null);
        assertThat(transformer.membershipConsensusResultFrom(transformer.membershipOperationOutcomeFrom(empty))).isEqualTo(empty);
    }

    @Test
    void executionConfirmationPreservesAllSevenCommandFields() {
        ConfirmMembershipPermitDrainedCommand command = new ConfirmMembershipPermitDrainedCommand(
                new ClusterOperationId("operation"), "step", "permit", new BrokerId("executor"),
                "incarnation", "digest", BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
        assertThat(transformer.membershipExecutionConfirmationFrom(command)).isEqualTo(
                new MembershipExecutionConfirmation(command.operationId(), "step", "permit", new BrokerId("executor"),
                        "incarnation", "digest", BrokerMembershipPermit.Result.TARGET_NOT_APPLIED));
    }

    @Test
    void mappedViewsPreserveOperationAndIsolateCallerCancellation() {
        BrokerClusterOperation operation = BrokerClusterOperation.builder().id(new ClusterOperationId("one"))
                .commandDigest(new CommandDigest("digest")).type(BrokerClusterOperationType.COMPLETE_LEARNER_SYNC)
                .targetBrokerId(new BrokerId("broker")).acceptedEpoch(new RoutingEpoch(1L))
                .acceptedRevision(new ClusterRevision(1L)).resultEpoch(new RoutingEpoch(2L))
                .phase(BrokerClusterOperationPhase.PENDING).createdAt(Instant.EPOCH).build();
        MembershipOperationAttempt attempt = new MembershipOperationAttempt(operation);
        CompletionStage<BrokerClusterConsensusResult> first = attempt.resultView().thenApply(transformer::membershipConsensusResultFrom);
        CompletionStage<BrokerClusterConsensusResult> shared = attempt.resultView().thenApply(transformer::membershipConsensusResultFrom);
        assertThat(first.toCompletableFuture().cancel(false)).isTrue();
        assertThat(attempt.completed()).isFalse();
        BrokerClusterConsensusResult result = BrokerClusterConsensusResult.applied(operation);
        MembershipOperationOutcome outcome = transformer.membershipOperationOutcomeFrom(result);
        assertThat(outcome.operation()).isSameAs(operation);
        attempt.complete(outcome);
        assertThat(shared.toCompletableFuture().join()).isEqualTo(result);
        assertThat(attempt.completed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ConfirmMembershipPermitDrainedParams.Result.class)
    void requestPreservesEveryIdentityAndExplicitTrustedSource(ConfirmMembershipPermitDrainedParams.Result result) {
        ConfirmMembershipPermitDrainedParams params = new ConfirmMembershipPermitDrainedParams(
                "cluster", "executor", "receiver", "operation", "step", "permit", "executor", "incarnation", "digest", result);
        AcceptMembershipPermitDrainedCommand accepted = transformer.acceptMembershipPermitDrainedCommandFrom(
                params, new BrokerId("trusted-source"));
        assertThat(accepted).isEqualTo(new AcceptMembershipPermitDrainedCommand(
                "cluster", "executor", "receiver", "operation", "step", "permit", "executor", "incarnation",
                "digest", AcceptMembershipPermitDrainedCommand.Result.valueOf(result.name()), "trusted-source"));
        assertThat(transformer.confirmMembershipPermitDrainedCommandFrom(accepted))
                .isEqualTo(new ConfirmMembershipPermitDrainedCommand(new ClusterOperationId("operation"), "step", "permit",
                        new BrokerId("executor"), "incarnation", "digest", BrokerMembershipPermit.Result.valueOf(result.name())));
        assertThat(transformer.drainedResultFrom(BrokerMembershipPermit.Result.valueOf(result.name()))).isEqualTo(result);
    }

    @Test
    void unknownDrainResultCannotBecomeWireEvidence() {
        assertThatThrownBy(() -> transformer.drainedResultFrom(BrokerMembershipPermit.Result.UNKNOWN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
