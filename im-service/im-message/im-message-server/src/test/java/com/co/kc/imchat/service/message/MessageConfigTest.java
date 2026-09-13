package com.co.kc.imchat.service.message;

import org.apache.shardingsphere.driver.yaml.YamlJDBCConfiguration;
import org.apache.shardingsphere.infra.util.yaml.YamlEngine;
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
    void declaresOwnedShardingDatasource() throws IOException {
        StandardEnvironment environment = loadApplicationConfig();
        YamlJDBCConfiguration sharding = YamlEngine.unmarshal(
                new ClassPathResource("im-sharding.yml").getFile(), YamlJDBCConfiguration.class);

        assertThat(environment.getProperty("im.datasource.sharding.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("im.datasource.sharding.config-location"))
                .isEqualTo("classpath:im-sharding.yml");
        assertThat(environment.getProperty("spring.datasource.url")).isNull();
        assertThat(sharding.getDataSources().get("ds_0").get("url").toString())
                .contains("/im_chat_message?");
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

    @Test
    void doesNotBypassShardingSqlShowWithStdoutLogging() throws IOException {
        StandardEnvironment environment = loadApplicationConfig();

        assertThat(environment.getProperty("mybatis.configuration.log-impl")).isNull();
        assertThat(environment.getProperty("mybatis-plus.configuration.log-impl")).isNull();
    }

    @Test
    void keepsSpringRedisAndRedissonTransportExplicitlyAligned() throws IOException {
        StandardEnvironment environment = loadApplicationConfig();

        assertThat(environment.getProperty("spring.data.redis.ssl.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("spring.redis.redisson.config"))
                .contains("address: \"redis://127.0.0.1:6379\"");
    }

    private StandardEnvironment loadApplicationConfig() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
