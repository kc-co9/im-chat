package com.co.kc.imchat.plugin.identity.properties;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakePropertiesTest {

    @Test
    void acceptsStaticConfiguration() {
        SnowflakeProperties properties = new SnowflakeProperties(
                SnowflakeMode.STATIC,
                7L,
                19L,
                null,
                null,
                null);

        assertThat(properties.mode()).isEqualTo(SnowflakeMode.STATIC);
        assertThat(properties.dataCenterId()).isEqualTo(7L);
        assertThat(properties.machineId()).isEqualTo(19L);
    }

    @Test
    void acceptsRedisLeaseConfiguration() {
        SnowflakeProperties properties = new SnowflakeProperties(
                SnowflakeMode.REDIS,
                7L,
                null,
                "iam",
                Duration.ofMinutes(10),
                Duration.ofSeconds(30));

        assertThat(properties.mode()).isEqualTo(SnowflakeMode.REDIS);
        assertThat(properties.dataCenterId()).isEqualTo(7L);
        assertThat(properties.resolveNamespace("im-iam")).isEqualTo("iam");
        assertThat(properties.leaseDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void usesApplicationNameAndTimingDefaults() {
        SnowflakeProperties properties = new SnowflakeProperties(
                SnowflakeMode.REDIS,
                0L,
                null,
                null,
                null,
                null);

        assertThat(properties.resolveNamespace("im-iam")).isEqualTo("im-iam");
        assertThat(properties.leaseDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void rejectsMissingModeOrDataCenter() {
        assertThatThrownBy(() -> new SnowflakeProperties(
                null, 1L, 1L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeProperties(
                SnowflakeMode.STATIC, null, 1L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeProperties(
                SnowflakeMode.STATIC, 32L, 1L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidStaticMachineId() {
        assertThatThrownBy(() -> new SnowflakeProperties(
                SnowflakeMode.STATIC, 1L, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeProperties(
                SnowflakeMode.STATIC, 1L, 32L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidRedisConfiguration() {
        assertThatThrownBy(() -> new SnowflakeProperties(
                SnowflakeMode.REDIS,
                1L,
                null,
                " ",
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeProperties(
                SnowflakeMode.REDIS,
                1L,
                null,
                null,
                Duration.ZERO,
                Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeProperties(
                SnowflakeMode.REDIS,
                1L,
                null,
                null,
                Duration.ofSeconds(30),
                Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
