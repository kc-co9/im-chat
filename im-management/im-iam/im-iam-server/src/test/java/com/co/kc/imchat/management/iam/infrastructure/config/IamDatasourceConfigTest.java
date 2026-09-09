package com.co.kc.imchat.management.iam.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class IamDatasourceConfigTest {

    @Test
    void declaresTheOwnedMysqlDatasource() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load(
                        "application.yml",
                        new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("spring.datasource.url"))
                .startsWith("jdbc:mysql://")
                .contains("/im_chat_iam?");
    }
}
