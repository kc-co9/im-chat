package com.co.kc.imchat.management.ops.infrastructure.client;

import com.co.kc.imchat.management.ops.infrastructure.config.beans.OpsHttpBeans;
import com.co.kc.imchat.management.ops.infrastructure.config.properties.OpsBrokerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class HttpBrokerManagementClientWiringTest {

    @Test
    void selectsBrokerRestClientWhenIamRestClientAlsoExists() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(HttpBrokerManagementClient.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OpsBrokerProperties.class)
    @Import({OpsHttpBeans.class, HttpBrokerManagementClient.class})
    static class TestConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean("iamRestClient")
        RestClient iamRestClient() {
            return RestClient.create("https://iam.example.com");
        }
    }
}
