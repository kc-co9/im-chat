package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterMembershipPermitCommandTest {
    @Test
    void builderPreservesFieldsAndDoesNotMutatePublishedCommand() {
        RegisterMembershipPermitCommand.Builder builder = RegisterMembershipPermitCommand.builder()
                .operationId(new ClusterOperationId("op")).stepId("step").permitId("permit")
                .executorBrokerId(new BrokerId("executor")).executorIncarnation("inc")
                .expectedRevision(new ClusterRevision(19L));
        RegisterMembershipPermitCommand first = builder.build();
        assertThat(first).isEqualTo(new RegisterMembershipPermitCommand(new ClusterOperationId("op"), "step", "permit",
                new BrokerId("executor"), "inc", new ClusterRevision(19L)));
        assertThat(builder.permitId("next").build().permitId()).isEqualTo("next");
        assertThat(first.permitId()).isEqualTo("permit");
    }

    @Test
    void builderCannotBypassCanonicalValidation() {
        assertThatThrownBy(() -> RegisterMembershipPermitCommand.builder().build())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("membership permit command is incomplete");
        assertThatThrownBy(() -> RegisterMembershipPermitCommand.builder()
                .operationId(new ClusterOperationId("op")).stepId("step").permitId(" ")
                .executorBrokerId(new BrokerId("executor")).executorIncarnation("inc")
                .expectedRevision(new ClusterRevision(19L)).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("membership permit command is incomplete");
    }
}
