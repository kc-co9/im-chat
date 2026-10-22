package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.common.utils.HashUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForceDeactivateBrokerCommandTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void digestRejectsMissingOrBlankForceReason(String reason) {
        ForceDeactivateBrokerCommand command = new ForceDeactivateBrokerCommand("op", "b", 3L, reason);
        assertThatThrownBy(command::digest).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("force reason is required");
    }

    @Test
    void digestPreservesCanonicalContentAndBindsReason() {
        ForceDeactivateBrokerCommand command = new ForceDeactivateBrokerCommand("op", "b", 3L, "accept risk");
        assertThat(command.digest().value()).isEqualTo(HashUtils.sha256Hex("force-deactivate\nb\n3\naccept risk"));
        assertThat(command.digest()).isEqualTo(new ForceDeactivateBrokerCommand("retry", "b", 3L, "accept risk").digest());
        assertThat(command.digest()).isNotEqualTo(new ForceDeactivateBrokerCommand("op", "b", 3L, "other reason").digest());
    }
}
