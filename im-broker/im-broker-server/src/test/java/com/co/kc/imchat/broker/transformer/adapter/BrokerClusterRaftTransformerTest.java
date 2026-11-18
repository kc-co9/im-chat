package com.co.kc.imchat.broker.transformer.adapter;

import com.co.kc.imchat.broker.model.cqrs.command.RegisterMembershipPermitCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmMembershipPermitDrainedCommand;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipStep;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerClusterRaftTransformerTest {
    @Test
    void mapsExplicitCurrentRevisionAndExactPermitBindingWithoutInventingSuccess() {
        BrokerClusterOperation operation = operation(BrokerClusterOperationType.START_LEARNER_SYNC);
        BrokerClusterRaftTransformer transformer = BrokerClusterRaftTransformer.INSTANCE;
        ClusterRevision current = new ClusterRevision(8L);
        assertThat(transformer.commitCommandFrom(operation, current, Instant.EPOCH).expectedRevision()).isEqualTo(current);
        assertThatThrownBy(() -> transformer.commitCommandFrom(operation, Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
        RegisterMembershipPermitCommand register = transformer.registerPermitCommandFrom(operation, "permit", new BrokerId("a"), "process", current);
        assertThat(register.expectedRevision()).isEqualTo(current);
        assertThat(register.stepId()).isEqualTo("final-target");
        assertThat(register.executorIncarnation()).isEqualTo("process");
        BrokerMembershipStep step = operation.currentMembershipStep();
        BrokerMembershipPermit permit = new BrokerMembershipPermit("permit", operation.id(), step.stepId(), step.target(),
                new BrokerId("a"), "process", BrokerMembershipPermit.Result.UNKNOWN);
        ConfirmMembershipPermitDrainedCommand confirm = transformer.confirmPermitCommandFrom(step, permit, BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
        assertThat(confirm.operationId()).isEqualTo(operation.id());
        assertThat(confirm.permitId()).isEqualTo(permit.permitId());
        assertThat(confirm.executorIncarnation()).isEqualTo(permit.executorIncarnation());
        assertThat(confirm.targetDigest()).isEqualTo(step.targetDigest());
        assertThat(confirm.result()).isEqualTo(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
    }

    @Test
    void learnerSyncCompletionRetainsOriginalAcceptedRevisionWithoutPermits() {
        BrokerClusterOperation operation = operation(BrokerClusterOperationType.COMPLETE_LEARNER_SYNC);
        assertThat(BrokerClusterRaftTransformer.INSTANCE.commitCommandFrom(operation, new ClusterRevision(8L), Instant.EPOCH)
                .expectedRevision()).isEqualTo(new ClusterRevision(0L));
        assertThat(operation.membershipSteps()).isEmpty();
    }

    private BrokerClusterOperation operation(BrokerClusterOperationType type) {
        return BrokerClusterOperation.builder().id(new ClusterOperationId("op")).commandDigest(new CommandDigest("digest"))
                .type(type).targetBrokerId(new BrokerId("b")).acceptedRevision(new ClusterRevision(0L))
                .acceptedEpoch(new RoutingEpoch(0L)).resultEpoch(new RoutingEpoch(0L)).phase(BrokerClusterOperationPhase.PENDING)
                .createdAt(Instant.EPOCH).membershipEndpoint(new MembershipEndpoint("b:18120"))
                .membershipSteps(type == BrokerClusterOperationType.COMPLETE_LEARNER_SYNC ? List.of() : List.of(
                        new BrokerMembershipStep("final-target", new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120")),
                                Set.of(new MembershipEndpoint("b:18120"))), List.of()))).build();
    }
}
