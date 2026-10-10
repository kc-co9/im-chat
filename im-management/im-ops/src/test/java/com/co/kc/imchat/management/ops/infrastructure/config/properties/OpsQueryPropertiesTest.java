package com.co.kc.imchat.management.ops.infrastructure.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpsQueryPropertiesTest {

    @Test
    void acceptsPositiveExecutionCapacity() {
        OpsQueryProperties properties = new OpsQueryProperties(8, 128);

        assertThat(properties.threads()).isEqualTo(8);
        assertThat(properties.queueCapacity()).isEqualTo(128);
    }

    @Test
    void rejectsMissingOrNonPositiveExecutionCapacity() {
        assertThatThrownBy(() -> new OpsQueryProperties(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpsQueryProperties(1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpsQueryProperties(0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpsQueryProperties(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
