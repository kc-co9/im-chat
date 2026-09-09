package com.co.kc.imchat.service.message;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MessageConfigTest {

    @Test
    void importsOptionalServiceConfiguration() throws IOException {
        StandardEnvironment environment = loadApplicationConfig();

        assertThat(environment.getProperty("spring.application.name")).isEqualTo("im-message");
        assertThat(environment.getProperty("spring.config.import[0]"))
                .isEqualTo("optional:nacos:im-message.yml?group=SERVICE_GROUP");
    }

    @Test
    void declaresTheOwnedMysqlDatasource() throws IOException {
        StandardEnvironment environment = loadApplicationConfig();

        assertThat(environment.getProperty("spring.datasource.url"))
                .startsWith("jdbc:mysql://")
                .contains("/im_chat_message?");
        assertThat(environment.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("com.mysql.cj.jdbc.Driver");
    }

    @Test
    void configuresStaticSnowflakeIdentity() throws IOException {
        StandardEnvironment environment = loadApplicationConfig();

        assertThat(environment.getProperty("im.identity.snowflake.mode"))
                .isEqualTo("STATIC");
        assertThat(environment.getProperty("im.identity.snowflake.data-center-id"))
                .isEqualTo("1");
        assertThat(environment.getProperty("im.identity.snowflake.machine-id"))
                .isEqualTo("1");
    }

    private StandardEnvironment loadApplicationConfig() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
