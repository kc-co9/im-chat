package com.co.kc.imchat.broker.domain.cluster.service;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipExecutionAdmission;
import java.util.Optional;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipStep;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryLearnerConfirmationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryMembershipOperationAttemptRepository;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipDrainSelectionTest {
    private final MembershipOperationService service = new MembershipOperationService(
            new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository());
    private final ConsensusMembership target = new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120")), Set.of());

    @Test
    void pendingPermitTakesPriorityOverEarlierConfirmedStep() {
        BrokerMembershipStep first = step("first", BrokerMembershipPermit.Result.TARGET_APPLIED);
        BrokerMembershipStep pending = step("pending", BrokerMembershipPermit.Result.UNKNOWN);
        BrokerClusterOperation operation = operation(List.of(first, pending));
        assertThat(operation.findMembershipStep("pending")).contains(pending);
        assertThat(operation.findMembershipStep("missing")).isEmpty();
        assertThat(operation.findCurrentMembershipPermit("first")).contains(first.currentPermit());
        assertThat(operation.findCurrentMembershipPermit("pending")).contains(pending.currentPermit());
        assertThat(operation.findCurrentMembershipPermit("missing")).isEmpty();
        assertThat(service.selectDrainedMembershipStep(operation, Map.of(
                first.currentPermit(), BrokerMembershipPermit.Result.TARGET_APPLIED,
                pending.currentPermit(), BrokerMembershipPermit.Result.TARGET_NOT_APPLIED))).isSameAs(pending);
        assertThatThrownBy(() -> service.selectDrainedMembershipStep(operation,
                Map.of(first.currentPermit(), BrokerMembershipPermit.Result.TARGET_APPLIED)))
                .isInstanceOf(SecurityException.class).hasMessage("native execution is not proven drained");
    }

    @Test
    void lastLocallyConfirmedStepWinsButAnyContradictionRejects() {
        BrokerMembershipStep first = step("first", BrokerMembershipPermit.Result.TARGET_APPLIED);
        BrokerMembershipStep last = step("last", BrokerMembershipPermit.Result.TARGET_APPLIED);
        BrokerClusterOperation operation = operation(List.of(first, last));
        assertThat(service.selectDrainedMembershipStep(operation, Map.of(
                first.currentPermit(), BrokerMembershipPermit.Result.TARGET_APPLIED,
                last.currentPermit(), BrokerMembershipPermit.Result.TARGET_APPLIED))).isSameAs(last);
        assertThatThrownBy(() -> service.selectDrainedMembershipStep(operation, Map.of(
                first.currentPermit(), BrokerMembershipPermit.Result.TARGET_NOT_APPLIED,
                last.currentPermit(), BrokerMembershipPermit.Result.TARGET_APPLIED)))
                .isInstanceOf(SecurityException.class).hasMessage("membership drain result is contradictory");
        assertThatThrownBy(() -> service.selectDrainedMembershipStep(operation, Map.of()))
                .isInstanceOf(SecurityException.class).hasMessage("local drained permit is missing");
    }

    @Test
    void structureScanRejectsBuilderAliasCorruptionInsteadOfTrustingOldValidation() {
        BrokerMembershipStep pending = step("pending", BrokerMembershipPermit.Result.UNKNOWN);
        BrokerMembershipStep later = step("later", BrokerMembershipPermit.Result.TARGET_APPLIED);
        BrokerClusterOperation.Builder builder = builder(List.of(pending));
        BrokerClusterOperation operation = builder.build();
        builder.membershipSteps(List.of(pending, later));
        assertThatThrownBy(operation::findPendingMembershipConfirmation)
                .isInstanceOf(SecurityException.class).hasMessage("membership step order is invalid");
    }

    @Test
    void confirmationAdmissionDistinguishesMissingOperationStepAndCurrentRecord() {
        BrokerCluster snapshot = mock(BrokerCluster.class);
        ClusterOperationId operationId = new ClusterOperationId("operation");
        when(snapshot.findOperation(operationId)).thenReturn(Optional.empty());
        MembershipExecutionAdmission missing = service.admitExecutionConfirmation(snapshot, operationId, "step");
        assertThat(missing.status()).isEqualTo(MembershipExecutionAdmission.Status.REJECTED);
        assertThat(missing.reason()).isEqualTo("confirmation permit is missing");
        BrokerMembershipStep step = step("step", BrokerMembershipPermit.Result.TARGET_APPLIED);
        when(snapshot.findOperation(operationId)).thenReturn(Optional.of(operation(List.of(step))));
        assertThat(service.admitExecutionConfirmation(snapshot, operationId, "missing").status())
                .isEqualTo(MembershipExecutionAdmission.Status.REJECTED);
        MembershipExecutionAdmission accepted = service.admitExecutionConfirmation(snapshot, operationId, "step");
        assertThat(accepted.status()).isEqualTo(MembershipExecutionAdmission.Status.ACCEPTED);
        assertThat(accepted.permit()).isSameAs(step.currentPermit());
        assertThat(accepted.reason()).isNull();
    }

    private BrokerMembershipStep step(String id, BrokerMembershipPermit.Result result) {
        BrokerMembershipPermit permit = new BrokerMembershipPermit(id, new ClusterOperationId("operation"), id,
                target, new BrokerId("a"), "incarnation", result);
        return new BrokerMembershipStep(id, target, List.of(permit));
    }

    private BrokerClusterOperation operation(List<BrokerMembershipStep> steps) {
        return builder(steps).build();
    }

    private BrokerClusterOperation.Builder builder(List<BrokerMembershipStep> steps) {
        return BrokerClusterOperation.builder().id(new ClusterOperationId("operation"))
                .commandDigest(new CommandDigest("digest")).type(BrokerClusterOperationType.REMOVE_BROKER)
                .targetBrokerId(new BrokerId("b")).minimumVoterCount(new MinimumVoterCount(1)).acceptedEpoch(new RoutingEpoch(1L))
                .acceptedRevision(new ClusterRevision(1L)).resultEpoch(new RoutingEpoch(2L))
                .phase(BrokerClusterOperationPhase.PENDING).createdAt(Instant.EPOCH).membershipSteps(steps);
    }
}
