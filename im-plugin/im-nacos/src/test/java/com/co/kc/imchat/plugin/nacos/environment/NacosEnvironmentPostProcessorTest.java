package com.co.kc.imchat.plugin.nacos.environment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NacosEnvironmentPostProcessorTest {

    @Test
    void loadsNacosDefaultsBeforeConfigDataProcessing() {
        StandardEnvironment environment = new StandardEnvironment();
        NacosEnvironmentPostProcessor processor = new NacosEnvironmentPostProcessor();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(processor.getOrder()).isLessThan(ConfigDataEnvironmentPostProcessor.ORDER);
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.group")).isEqualTo("IM_CHAT_GROUP");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.fail-fast", Boolean.class)).isTrue();
        assertThat(environment.getProperty("im.nacos.namespace"))
                .isEqualTo("b0b51fe2-fd46-463e-96df-dfba4a3b41a1");
        assertThat(environment.getProperty("spring.config.import[0]"))
                .isEqualTo("optional:nacos:common.yml?group=COMMON_GROUP");
    }

    @Test
    void keepsExternalConfigurationAtHigherPriority() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("external",
                Map.of("spring.cloud.nacos.discovery.group", "CUSTOM_GROUP")));

        new NacosEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.cloud.nacos.discovery.group")).isEqualTo("CUSTOM_GROUP");
    }
}
