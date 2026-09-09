package com.co.kc.imchat.management.monitor.infrastructure.config.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorBrokerPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void providesLocalDefaultsAndBindsOverrides() {
        contextRunner.run(context -> {
            MonitorBrokerProperties properties = context.getBean(MonitorBrokerProperties.class);
            assertThat(properties.getServiceName()).isEqualTo("im-broker");
            assertThat(properties.getRequestTimeoutMillis()).isEqualTo(3_000);
        });

        contextRunner.withPropertyValues(
                        "im.monitor.broker.service-name=custom-broker",
                        "im.monitor.broker.request-timeout-millis=1500")
                .run(context -> assertThat(context.getBean(MonitorBrokerProperties.class))
                        .satisfies(properties -> {
                            assertThat(properties.getServiceName()).isEqualTo("custom-broker");
                            assertThat(properties.getRequestTimeoutMillis()).isEqualTo(1_500);
                        }));
    }

    @Test
    void rejectsInvalidTimeout() {
        contextRunner.withPropertyValues("im.monitor.broker.request-timeout-millis=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MonitorBrokerProperties.class)
    static class PropertiesConfiguration {
    }
}
