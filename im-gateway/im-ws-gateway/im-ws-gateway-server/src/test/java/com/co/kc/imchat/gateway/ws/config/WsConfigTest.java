package com.co.kc.imchat.gateway.ws.config;

import com.co.kc.imchat.gateway.ws.config.properties.GatewayProperties;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WsConfigTest {

    @Test
    void defaultProfileUsesBrokerDiscoveryService() throws IOException {
        StandardEnvironment environment = load("application.yml");

        assertThat(environment.getProperty("im.gateway.ws.broker.protocol")).isNull();
        assertThat(environment.getProperty("im.gateway.ws.broker.bolt.load-balance")).isEqualTo("HASH");
        assertThat(environment.getProperty("im.bolt.client.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("im.bolt.server.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("im.bolt.server.port", Integer.class)).isEqualTo(12202);
        assertThat(environment.getProperty("spring.config.import[0]"))
                .isEqualTo("optional:nacos:im-ws-gateway.yml?group=INFRA_GROUP");
    }

    @Test
    void defaultProfileRunsAsNonWebNettyGateway() throws IOException {
        StandardEnvironment environment = load("application.yml");

        assertThat(environment.getProperty("spring.main.web-application-type")).isEqualTo("none");
        assertThat(environment.getProperty("server.port")).isNull();
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.port", Integer.class)).isEqualTo(19090);
        assertThat(environment.getProperty("im.gateway.ws.gateway-id"))
                .isNull();
    }

    @Test
    void defaultProfileDefinesFrameLimitsAndIdleTimeout() throws IOException {
        StandardEnvironment environment = load("application.yml");

        assertThat(environment.getProperty("im.gateway.ws.host")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("im.gateway.ws.bolt.host")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("im.gateway.ws.max-frame-payload-length", Integer.class))
                .isEqualTo(65536);
        assertThat(environment.getProperty("im.gateway.ws.idle.reader-idle-seconds", Integer.class))
                .isEqualTo(60);
    }

    @Test
    void defaultProfileBindsWsProperties() throws IOException {
        StandardEnvironment environment = load("application.yml");

        GatewayProperties properties = Binder.get(environment)
                .bind(GatewayProperties.PREFIX, GatewayProperties.class)
                .orElseThrow(() -> new IllegalStateException("WS 网关配置绑定失败"));

        assertThat(properties.getPort()).isEqualTo(19090);
        assertThat(properties.gatewayId(12202)).isEqualTo("gateway-127.0.0.1-12202");
        assertThat(properties.getPath()).isEqualTo("/ws");
        assertThat(properties.getMaxFramePayloadLength()).isEqualTo(65536);
        assertThat(properties.getIdle().getReaderIdleSeconds()).isEqualTo(60);
        assertThat(properties.getBolt().getHost()).isEqualTo("127.0.0.1");
        assertThat(properties.getBroker().getBolt().getLoadBalance()).isEqualTo(BrokerLoadBalance.HASH);
        assertThat(properties.getBroker().getBolt().getTimeoutMillis()).isEqualTo(3000);
    }

    @Test
    void serverModuleDoesNotDependOnHttpFallbackStack() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom).doesNotContain("spring-boot-starter-web");
        assertThat(pom).doesNotContain("spring-cloud-starter-openfeign");
        assertThat(pom).doesNotContain("spring-cloud-starter-loadbalancer");
    }

    private StandardEnvironment load(String resourceName) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load(resourceName, new ClassPathResource(resourceName))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
