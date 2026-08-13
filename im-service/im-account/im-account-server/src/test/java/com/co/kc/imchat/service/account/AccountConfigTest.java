package com.co.kc.imchat.service.account;

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
    void declaresExplicitMysqlDatasource() throws IOException {
        StandardEnvironment environment = loadApplicationConfig();

        assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:mysql://");
        assertThat(environment.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(environment.getProperty("spring.datasource.type"))
                .isEqualTo("com.alibaba.druid.pool.DruidDataSource");
    }

    private StandardEnvironment loadApplicationConfig() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
