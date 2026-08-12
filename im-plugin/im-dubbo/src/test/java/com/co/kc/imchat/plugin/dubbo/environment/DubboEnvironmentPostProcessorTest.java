package com.co.kc.imchat.plugin.dubbo.environment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DubboEnvironmentPostProcessorTest {

    @Test
    void loadsDubboDefaults() {
        StandardEnvironment environment = new StandardEnvironment();

        new DubboEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("im.dubbo.registry.address"))
                .isEqualTo("nacos://127.0.0.1:8848");
        assertThat(environment.getProperty("im.dubbo.registry.namespace")).isEmpty();
        assertThat(environment.getProperty("im.dubbo.registry.group")).isEqualTo("DUBBO_GROUP");
        assertThat(environment.getProperty("im.dubbo.protocol.name")).isEqualTo("dubbo");
        assertThat(environment.getProperty("im.dubbo.consumer.timeout", Integer.class)).isEqualTo(3000);
    }

    @Test
    void keepsExternalConfigurationAtHigherPriority() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("external",
                Map.of("im.dubbo.consumer.timeout", 5000)));

        new DubboEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("im.dubbo.consumer.timeout", Integer.class)).isEqualTo(5000);
    }
}
