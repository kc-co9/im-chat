package com.co.kc.imchat.service.account;

import org.apache.shardingsphere.driver.yaml.YamlJDBCConfiguration;
import org.apache.shardingsphere.infra.util.yaml.YamlEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AccountConfigTest {

    @Test
    void importsOptionalServiceConfiguration() throws IOException {
        StandardEnvironment environment = loadApplicationConfig();

        assertThat(environment.getProperty("spring.application.name")).isEqualTo("im-account");
        assertThat(environment.getProperty("spring.config.import[0]"))
                .isEqualTo("optional:nacos:im-account.yml?group=SERVICE_GROUP");
    }

    @Test
    void declaresOwnedShardingDatasource() throws IOException {
        StandardEnvironment environment = loadApplicationConfig();
        YamlJDBCConfiguration sharding = YamlEngine.unmarshal(
                new ClassPathResource("im-sharding.yml").getFile(), YamlJDBCConfiguration.class);

        assertThat(environment.getProperty("im.datasource.sharding.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("im.datasource.sharding.config-location"))
                .isEqualTo("classpath:im-sharding.yml");
        assertThat(environment.getProperty("spring.datasource.url")).isNull();
        assertThat(sharding.getDataSources().get("ds_0").get("url").toString())
                .contains("/im_chat_account?");
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
