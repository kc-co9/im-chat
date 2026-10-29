package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.alipay.remoting.rpc.RpcClient;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.plugin.bolt.ImBoltAutoConfiguration;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GatewayDevelopmentTargetConfigurationTest {
    @Test
    void actualComposeEnvironmentBindsPairForFormalGatewayWriteAndClose() throws Exception {
        Map<String, Object> compose = renderedCompose();
        Map<String, Object> brokerEnvironment = environment(compose, "broker");
        Map<String, Object> gatewayEnvironment = environment(compose, "gateway");
        String host = gatewayEnvironment.get("IM_GATEWAY_HOST").toString();
        int port = gatewayPort();
        GatewayEndpointDTO endpoint =
                new GatewayEndpointDTO("gateway-dev", host, port,
                        "trusted-network://" + host, Instant.EPOCH, Instant.EPOCH);
        composeContext(brokerEnvironment).run(context -> {
            assertThat(context).hasNotFailed();
            BrokerProperties properties = context.getBean(BrokerProperties.class);
            assertThat(properties.getGatewayPush().getBolt().getDevelopmentTargets()).hasSize(1);
            BoltTarget expected = properties.getGatewayPush().getBolt()
                    .getDevelopmentTargets().getFirst();
            assertThat(expected.address()).isEqualTo(host + ":" + port);
            assertThat(expected.expectedPeerIdentity()).isEqualTo(
                    "spiffe://im-chat/gateway/bolt-dev-gateway");
            BoltClientInvoker invoker = org.mockito.Mockito.spy(
                    context.getBean(BoltClientInvoker.class));
            List<BoltTarget> captured = new ArrayList<>();
            org.mockito.Mockito.doAnswer(call -> {
                captured.add(call.getArgument(0));
                return call.getArgument(4) == Void.class ? null :
                        new GatewayFrameWriteResult(List.of("conn"), List.of());
            }).when(invoker).invoke(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
            GatewayClient client = new ClientConfiguration().gatewayClient(invoker, properties, context.getBean(RpcClient.class));
            GatewayFrameWriteParams write =
                    new GatewayFrameWriteParams(1L, "delivery", "attempt", null);
            ConnectionCloseParams close =
                    new ConnectionCloseParams("close", 1L, "session");
            assertThat(client.writeFrame(endpoint, write).acceptedConnectionIds()).containsExactly("conn");
            client.closeConnections(endpoint, close);
            assertThat(captured).containsExactly(expected, expected);
            assertThat(endpoint.workloadIdentity()).isEqualTo("trusted-network://" + host);
        });
    }

    @Test
    void missingOrWrongComposePairRejectsBeforeInvoker() throws Exception {
        Map<String, Object> compose = renderedCompose();
        Map<String, Object> original = environment(compose, "broker");
        String host = environment(compose, "gateway").get("IM_GATEWAY_HOST").toString();
        for (boolean missing : new boolean[]{true, false}) {
            Map<String, Object> mutant = new HashMap<>(original);
            String key = "IM_BROKER_GATEWAYPUSH_BOLT_DEVELOPMENTTARGETS_0_ADDRESS";
            if (missing) {
                mutant.remove(key);
                mutant.remove("IM_BROKER_GATEWAYPUSH_BOLT_DEVELOPMENTTARGETS_0_EXPECTEDPEERIDENTITY");
            } else {
                mutant.put(key, "172.30.88.11:18013");
            }
            composeContext(mutant).run(context -> {
                assertThat(context).hasNotFailed();
                BoltClientInvoker invoker = org.mockito.Mockito.spy(
                        context.getBean(BoltClientInvoker.class));
                GatewayClient client = new ClientConfiguration().gatewayClient(invoker,
                        context.getBean(BrokerProperties.class), context.getBean(RpcClient.class));
                org.mockito.Mockito.clearInvocations(invoker);
                GatewayEndpointDTO endpoint =
                        new GatewayEndpointDTO("gateway-dev", host, gatewayPort(),
                                "trusted-network://" + host, Instant.EPOCH, Instant.EPOCH);
                assertThatThrownBy(() -> client.writeFrame(endpoint, null)).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("no deployment pair");
                assertThatThrownBy(() -> client.closeConnections(endpoint, null)).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("no deployment pair");
                org.mockito.Mockito.verifyNoInteractions(invoker);
            });
        }
    }

    private static int gatewayPort() {
        try {
            PropertySource<?> config = new YamlPropertySourceLoader().load("gateway",
                    new FileSystemResource(repositoryRoot().resolve(
                            "im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/resources/application.yml"))).getFirst();
            return Integer.parseInt(config.getProperty("im.bolt.server.port").toString());
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private ApplicationContextRunner composeContext(Map<String, Object> environment) {
        return new ApplicationContextRunner().withUserConfiguration(ImBoltAutoConfiguration.class, PairBinding.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("compose-systemEnvironment", environment)))
                .withPropertyValues("spring.config.location=" + repositoryRoot().resolve(
                                "im-broker/im-broker-server/src/main/resources/application-bolt-dev.yml").toUri(),
                        "im.bolt.client.enabled=true", "im.bolt.client.allowed-peer-uri-prefixes[0]=spiffe://im-chat/gateway/")
                .withInitializer(new ConfigDataApplicationContextInitializer());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BrokerProperties.class)
    static class PairBinding { }

    private static Path repositoryRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (!Files.isRegularFile(path.resolve("deploy/local/compose.bolt-dev.yml"))) {
            path = Objects.requireNonNull(path.getParent(), "repository root");
        }
        return path;
    }

    private static Map<String, Object> renderedCompose() throws Exception {
        Path output = Files.createTempFile("bolt-dev-compose-", ".json");
        Process process = null;
        try {
            process = new ProcessBuilder("docker", "compose", "-f",
                    repositoryRoot().resolve("deploy/local/compose.bolt-dev.yml").toString(), "config", "--format", "json")
                    .redirectError(ProcessBuilder.Redirect.INHERIT).redirectOutput(output.toFile()).start();
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            return new ObjectMapper().readValue(Files.readAllBytes(output), new TypeReference<Map<String, Object>>() { });
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            }
            Files.deleteIfExists(output);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> environment(Map<String, Object> compose, String service) {
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        return (Map<String, Object>) ((Map<String, Object>) services.get(service)).get("environment");
    }

    @Test
    void onlyActualRunningOwnedTransportCanSupplyFixedGatewayMode() {
        new ApplicationContextRunner().withUserConfiguration(ImBoltAutoConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("bolt-dev"))
                .withPropertyValues("im.bolt.client.enabled=true", "im.bolt.client.mode=DEVELOPMENT_PLAINTEXT",
                        "im.bolt.client.development.synthetic-data=true", "im.bolt.client.allowed-peer-uri-prefixes[0]=spiffe://im-chat/gateway/")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RpcClient actual = context.getBean(RpcClient.class);
                    BrokerProperties properties = new BrokerProperties();
                    ClientConfiguration factory = new ClientConfiguration();
                    GatewayClient client = factory.gatewayClient(context.getBean(BoltInvoker.class), properties, actual);
                    assertThat(client).isNotNull();
                    properties.getGatewayPush().getBolt().setDevelopmentTargets(List.of());
                    assertThat(((BoltControlledClient) actual).configuredMode()).isEqualTo(
                            BoltTransportMode.DEVELOPMENT_PLAINTEXT);
                    assertThatThrownBy(() -> factory.gatewayClient(mock(BoltInvoker.class), properties, actual))
                            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same running controlled");
                    assertThatThrownBy(() -> factory.gatewayClient(context.getBean(BoltInvoker.class), properties, mock(RpcClient.class)))
                            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same running controlled");
                });
    }
    @ParameterizedTest
    @ValueSource(strings = {"NEW_KEY", "NEW_ENV", "OLD_KEY", "OLD_ENV"})
    void ignoresRemovedPeerIdentityUriPrefixConfiguration(String source) {
        String key = "im.broker.gateway-push.bolt." + (source.startsWith("OLD")
                ? "workload-identity-prefix" : "peer-identity-uri-prefix");
        String custom = "spiffe://prefix-binding.example/peer/";
        org.springframework.core.env.StandardEnvironment environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().remove("systemProperties");
        environment.getPropertySources().remove("systemEnvironment");
        if (source.endsWith("ENV")) {
            String variable = key.replace("-", "").replace('.', '_').toUpperCase(java.util.Locale.ROOT);
            environment.getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                    "systemEnvironment", java.util.Map.of(variable, custom)));
        } else {
            environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "binding-test", java.util.Map.of(key, custom)));
        }
        BrokerProperties bound = org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bindOrCreate("im.broker", BrokerProperties.class);
        BrokerProperties.GatewayPush.Bolt properties = bound.getGatewayPush().getBolt();
        assertThat(org.springframework.beans.BeanUtils.getPropertyDescriptor(
                BrokerProperties.GatewayPush.Bolt.class, "peerIdentityUriPrefix")).isNull();
        assertThatThrownBy(() -> new com.co.kc.imchat.gateway.ws.sdk.GatewayClient(null, 100,
                BoltTransportMode.DEVELOPMENT_PLAINTEXT,
                List.of(new com.co.kc.imchat.plugin.bolt.model.BoltTarget("gateway:18010", custom + "node"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
