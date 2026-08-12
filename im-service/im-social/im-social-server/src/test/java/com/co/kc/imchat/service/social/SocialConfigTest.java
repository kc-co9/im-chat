package com.co.kc.imchat.service.social;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SocialConfigTest {

    @Test
    void importsOptionalServiceConfiguration() throws IOException {
        StandardEnvironment environment = loadApplicationConfig();

        assertThat(environment.getProperty("spring.application.name")).isEqualTo("im-social");
        assertThat(environment.getProperty("spring.config.import[0]"))
                .isEqualTo("optional:nacos:im-social.yml?group=SERVICE_GROUP");
    }

    private StandardEnvironment loadApplicationConfig() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
