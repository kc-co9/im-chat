package com.co.kc.imchat.broker.model.cqrs.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcceptMembershipPermitDrainedCommandTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void incompleteIdentityFailsAtConstruction(int field) {
        String[] values = {"cluster", "executor", "receiver", "operation", "step", "permit",
                "executor", "incarnation", "digest", "executor"};
        String[] fields = {"clusterId", "callerBrokerId", "receiverBrokerId", "operationId", "stepId", "permitId",
                "executorBrokerId", "executorIncarnation", "targetDigest", "authenticatedExecutorBrokerId"};
        for (String invalid : new String[]{null, "", " "}) {
            values[field] = invalid;
            assertThatThrownBy(() -> new AcceptMembershipPermitDrainedCommand(
                    values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8],
                    AcceptMembershipPermitDrainedCommand.Result.TARGET_APPLIED, values[9]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(fields[field] + " is required");
        }
    }

    @Test
    void missingResultFailsAtConstruction() {
        assertThatThrownBy(() -> new AcceptMembershipPermitDrainedCommand(
                "cluster", "executor", "receiver", "operation", "step", "permit", "executor", "incarnation", "digest",
                null, "executor"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("result is required");
    }

    @Test
    void completeCommandPreservesUnnormalizedIdentityForApplicationChecks() {
        AcceptMembershipPermitDrainedCommand command = new AcceptMembershipPermitDrainedCommand(
                "cluster", "caller", "receiver", "operation", "step", "permit", "executor", "incarnation", "digest",
                AcceptMembershipPermitDrainedCommand.Result.TARGET_NOT_APPLIED, "authenticated");
        assertThat(command.callerBrokerId()).isEqualTo("caller");
        assertThat(command.executorBrokerId()).isEqualTo("executor");
        assertThat(command.authenticatedExecutorBrokerId()).isEqualTo("authenticated");
        assertThat(command.result()).isEqualTo(AcceptMembershipPermitDrainedCommand.Result.TARGET_NOT_APPLIED);
    }
}
