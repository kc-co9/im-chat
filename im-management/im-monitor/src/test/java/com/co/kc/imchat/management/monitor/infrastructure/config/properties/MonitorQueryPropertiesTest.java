package com.co.kc.imchat.management.monitor.infrastructure.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonitorQueryPropertiesTest {

    @Test
    void acceptsPositiveExecutionCapacity() {
        MonitorQueryProperties properties = new MonitorQueryProperties(8, 128);

        assertThat(properties.threads()).isEqualTo(8);
        assertThat(properties.queueCapacity()).isEqualTo(128);
    }

    @Test
    void rejectsMissingOrNonPositiveExecutionCapacity() {
        assertThatThrownBy(() -> new MonitorQueryProperties(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonitorQueryProperties(1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonitorQueryProperties(0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonitorQueryProperties(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
