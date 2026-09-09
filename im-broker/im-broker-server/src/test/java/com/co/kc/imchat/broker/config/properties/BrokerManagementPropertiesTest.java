package com.co.kc.imchat.broker.config.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerManagementPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsSafeLocalDefaults() {
        contextRunner.run(context -> {
            BrokerManagementProperties properties = context.getBean(BrokerManagementProperties.class);
            assertThat(properties.getHost()).isEqualTo("127.0.0.1");
            assertThat(properties.getPort()).isEqualTo(12201);
            assertThat(properties.getHistoryCapacity()).isEqualTo(100);
        });
    }

    @Test
    void rejectsCapacityAboveOperationalLimit() {
        contextRunner.withPropertyValues("im.broker.management.history-capacity=1001")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsBlankHostAndInvalidPort() {
        contextRunner.withPropertyValues(
                        "im.broker.management.host= ",
                        "im.broker.management.port=65536")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsManagementPortConflictingWithBolt() {
        BrokerManagementProperties properties = new BrokerManagementProperties();
        properties.setPort(12200);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> properties.validateBoltPort(12200))
                .isInstanceOf(IllegalStateException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BrokerManagementProperties.class)
    static class TestConfiguration {
    }
}
