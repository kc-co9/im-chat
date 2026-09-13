package com.co.kc.imchat.management.audit.infrastructure.config;

import org.apache.shardingsphere.driver.yaml.YamlJDBCConfiguration;
import org.apache.shardingsphere.infra.util.yaml.YamlEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDatasourceConfigTest {

    @Test
    void declaresOwnedShardingDatasource() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load(
                        "application.yml",
                        new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        YamlJDBCConfiguration sharding = YamlEngine.unmarshal(
                new ClassPathResource("im-sharding.yml").getFile(), YamlJDBCConfiguration.class);

        assertThat(environment.getProperty("im.datasource.sharding.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("im.datasource.sharding.config-location"))
                .isEqualTo("classpath:im-sharding.yml");
        assertThat(environment.getProperty("spring.datasource.url")).isNull();
        assertThat(sharding.getDataSources().get("ds_0").get("url").toString())
                .contains("/im_chat_audit?");
    }
}
