package com.co.kc.imchat.management.ops.infrastructure.config.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OpsBrokerPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void providesLocalDefaultsAndBindsOverrides() {
        contextRunner.run(context -> {
            OpsBrokerProperties properties = context.getBean(OpsBrokerProperties.class);
            assertThat(properties.getServiceName()).isEqualTo("im-broker");
            assertThat(properties.getRequestTimeoutMillis()).isEqualTo(3_000);
        });

        contextRunner.withPropertyValues(
                        "im.ops.broker.service-name=custom-broker",
                        "im.ops.broker.request-timeout-millis=1500")
                .run(context -> assertThat(context.getBean(OpsBrokerProperties.class))
                        .satisfies(properties -> {
                            assertThat(properties.getServiceName()).isEqualTo("custom-broker");
                            assertThat(properties.getRequestTimeoutMillis()).isEqualTo(1_500);
                        }));
    }

    @Test
    void rejectsInvalidTimeout() {
        contextRunner.withPropertyValues("im.ops.broker.request-timeout-millis=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OpsBrokerProperties.class)
    static class PropertiesConfiguration {
    }
}
