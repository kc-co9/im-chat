package com.co.kc.imchat.broker.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.config.properties.RegistryProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerConfigTest {
    @Test
    void defaultProfileUsesTargetRuntimeProtocols() throws IOException {
        StandardEnvironment environment = load("application.yml");

        assertThat(environment.getProperty("im.bolt.client.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("im.bolt.server.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.config.import[0]"))
                .isEqualTo("optional:nacos:im-broker.yml?group=INFRA_GROUP");
        assertThat(environment.getProperty("im.broker.cluster.sync-strategy")).isNull();
        assertThat(environment.getProperty("im.broker.instance.id")).isNull();
        BrokerProperties brokerProperties = Binder.get(environment)
                .bind("im.broker", BrokerProperties.class)
                .orElseThrow(IllegalStateException::new);
        assertThat(brokerProperties.getInstance().getId()).isEqualTo("broker-127.0.0.1-12200");
        RegistryProperties registryProperties = Binder.get(environment)
                .bind("im.broker.registry", RegistryProperties.class)
                .orElseThrow(IllegalStateException::new);
        assertThat(registryProperties.getBrokerTtl()).isNotNull();
        assertThat(environment.getProperty("im.broker.gateway-push.protocol")).isNull();
        assertThat(environment.getProperty("spring.main.web-application-type")).isNull();
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.port", Integer.class)).isEqualTo(12200);
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(12201);
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.metadata.management-host"))
                .isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.metadata.management-port", Integer.class))
                .isEqualTo(12201);
    }

    @Test
    void brokerSdkDoesNotDependOnHttpFallbackStack() throws IOException {
        String serverPom = Files.readString(Path.of("pom.xml"));
        String sdkPom = Files.readString(Path.of("../im-broker-sdk/pom.xml"));

        assertThat(serverPom).contains("spring-boot-starter-web");
        assertThat(serverPom).doesNotContain("spring-cloud-starter-openfeign");
        assertThat(serverPom).doesNotContain("spring-cloud-starter-loadbalancer");
        assertThat(sdkPom).doesNotContain("spring-cloud-starter-openfeign");
    }

    private StandardEnvironment load(String resourceName) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load(resourceName, new ClassPathResource(resourceName))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
