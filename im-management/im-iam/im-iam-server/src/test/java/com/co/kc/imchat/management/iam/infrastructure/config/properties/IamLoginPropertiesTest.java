package com.co.kc.imchat.management.iam.infrastructure.config.properties;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamLoginPropertiesTest {

    @Test
    void keepsTheApprovedLoginLockDefaults() {
        IamLoginProperties properties = new IamLoginProperties(5, Duration.ofMinutes(15));

        assertThat(properties.failureLimit()).isEqualTo(5);
        assertThat(properties.lockDuration()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void rejectsInvalidLoginPolicyAtBindingBoundary() {
        assertThatThrownBy(() -> new IamLoginProperties(0, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IamLoginProperties(5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
