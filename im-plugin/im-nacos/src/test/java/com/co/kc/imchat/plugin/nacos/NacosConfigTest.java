package com.co.kc.imchat.plugin.nacos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class NacosConfigTest {

    @Test
    void definesSharedNacosConfiguration() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        TestPropertyValues.of("spring.application.name=im-test").applyTo(environment);
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("im-nacos.yml", new ClassPathResource("META-INF/config/im-nacos.yml"))
                .forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("spring.config.import[0]"))
                .isEqualTo("optional:nacos:common.yml?group=COMMON_GROUP");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.server-addr"))
                .isEqualTo("127.0.0.1:8848");
        assertThat(environment.getProperty("im.nacos.namespace"))
                .isEqualTo("b0b51fe2-fd46-463e-96df-dfba4a3b41a1");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.namespace"))
                .isEqualTo("b0b51fe2-fd46-463e-96df-dfba4a3b41a1");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.group")).isEqualTo("IM_CHAT_GROUP");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.fail-fast", Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.cloud.nacos.config.namespace"))
                .isEqualTo("b0b51fe2-fd46-463e-96df-dfba4a3b41a1");
        assertThat(environment.getProperty("spring.cloud.nacos.config.file-extension")).isEqualTo("yml");
    }
}
