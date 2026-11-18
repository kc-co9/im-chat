package com.co.kc.imchat.broker.sdk.model.params;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmMembershipPermitDrainedParamsTest {
    @Test
    void builderPreservesFieldsAndDoesNotMutatePublishedRequest() {
        ConfirmMembershipPermitDrainedParams.Builder builder = ConfirmMembershipPermitDrainedParams.builder()
                .clusterId("cluster").callerBrokerId("executor").receiverBrokerId("receiver")
                .operationId("operation").stepId("step").permitId("permit").executorBrokerId("executor")
                .executorIncarnation("incarnation").targetDigest("digest")
                .result(ConfirmMembershipPermitDrainedParams.Result.TARGET_APPLIED);
        ConfirmMembershipPermitDrainedParams first = builder.build();
        assertThat(first).isEqualTo(new ConfirmMembershipPermitDrainedParams("cluster", "executor", "receiver",
                "operation", "step", "permit", "executor", "incarnation", "digest",
                ConfirmMembershipPermitDrainedParams.Result.TARGET_APPLIED));
        assertThat(builder.receiverBrokerId("next").build().receiverBrokerId()).isEqualTo("next");
        assertThat(first.receiverBrokerId()).isEqualTo("receiver");
    }

    @Test
    void builderCannotBypassIdentityOrKnownResultValidation() {
        assertThatThrownBy(() -> ConfirmMembershipPermitDrainedParams.builder().build())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("permit confirmation identity is incomplete");
        assertThatThrownBy(() -> ConfirmMembershipPermitDrainedParams.builder()
                .clusterId("cluster").callerBrokerId("executor").receiverBrokerId("receiver")
                .operationId("operation").stepId("step").permitId("permit").executorBrokerId("executor")
                .executorIncarnation("incarnation").targetDigest("digest").build())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("permit confirmation requires a known result");
    }
}
