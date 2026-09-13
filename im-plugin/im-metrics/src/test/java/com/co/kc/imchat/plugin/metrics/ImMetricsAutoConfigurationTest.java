package com.co.kc.imchat.plugin.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ImMetricsAutoConfigurationTest {

    @Test
    void providesActuatorAndPrometheusRuntime() {
        ClassLoader classLoader = getClass().getClassLoader();

        assertThat(ClassUtils.isPresent(
                "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration", classLoader))
                .isTrue();
        assertThat(ClassUtils.isPresent(
                "io.micrometer.prometheusmetrics.PrometheusMeterRegistry", classLoader))
                .isTrue();
    }
}
