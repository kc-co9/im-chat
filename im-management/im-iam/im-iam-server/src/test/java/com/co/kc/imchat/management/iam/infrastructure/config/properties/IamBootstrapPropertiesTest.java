package com.co.kc.imchat.management.iam.infrastructure.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamBootstrapPropertiesTest {

    @Test
    void allowsMissingCredentialsWhileBootstrapIsDisabled() {
        assertThatCode(() -> new IamBootstrapProperties(false, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void requiresCompleteCredentialsWhileBootstrapIsEnabled() {
        assertThatThrownBy(() -> new IamBootstrapProperties(true, "admin", null,
                "strong-password"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new IamBootstrapProperties(
                true,
                "admin",
                "admin@imchat.com",
                "admin"))
                .doesNotThrowAnyException();
    }
}
