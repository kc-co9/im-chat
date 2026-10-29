package com.co.kc.imchat.service.account.infrastructure.config;

import java.util.ArrayList;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.DefaultServiceInstance;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.broker.sdk.model.params.BrokerRegisterParams;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.alipay.remoting.rpc.RpcClient;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.plugin.bolt.ImBoltAutoConfiguration;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.service.account.infrastructure.config.properties.AccountBrokerProperties;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 读取本 owning module 的正式 YAML/profile，实际启动受控传输；不声称数据库或远端服务已启动。
 */
class BoltConsumerConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ImBoltAutoConfiguration.class, TypedProperties.class)
            .withPropertyValues("spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.discovery.enabled=false",
                    "im.bolt.server.port=0");

    @Test
    void defaultMtlsFailsClosedWithoutMaterials() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("key-store");
        });
    }

    @Test
    void explicitSyntheticLoopbackProfileStartsActualControlledTransportAndBindsTrustedTargets() {
        runner.withPropertyValues("spring.profiles.active=bolt-dev", "IM_BOLT_SYNTHETIC_DATA=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("bolt-dev");
                    BoltControlledClient client = (BoltControlledClient) context.getBean(RpcClient.class);
                    assertThat(client.isRunningControlledAssembly()).isTrue();
                    assertThat(client.configuredMode()).isEqualTo(BoltTransportMode.DEVELOPMENT_PLAINTEXT);
                    AccountBrokerProperties properties = context.getBean(AccountBrokerProperties.class);
                    assertThat(properties.getBootstrapTargets()).containsExactly(
                            new BoltTarget("127.0.0.1:18020", "spiffe://im-chat/broker/broker-local-1"));
                    assertThat(client.getAllManagedConnections()).isEmpty();
                    BoltInvoker invoker = context.getBean(BoltInvoker.class);
                    Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> invoker.invoke(
                            new BoltTarget("10.0.0.1:18020", "spiffe://im-chat/broker/broker-local-1"),
                            "test", "reject-invalid-address", "request", String.class, 1000));
                    assertThat(failure).isInstanceOf(BoltInvocationException.class);
                    assertThat(((BoltInvocationException) failure).failure())
                            .isEqualTo(BoltInvocationException.Failure.PROTOCOL);
                    assertThat(failure.getCause()).isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("development address");
                    assertThat(client.getAllManagedConnections()).isEmpty();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCTION", "NO_SYNTHETIC", "NO_PROFILE", "OUTSIDE_BIND", "CONTAINER_UNDECLARED"})
    void actualEnvironmentRejectsUnsafeDevelopmentConfiguration(String boundary) {
        ApplicationContextRunner configured = runner.withPropertyValues("spring.profiles.active="
                        + (boundary.equals("PRODUCTION") ? "production,bolt-dev" : boundary.equals("NO_PROFILE") ? "test" : "bolt-dev"),
                "IM_BOLT_SYNTHETIC_DATA=" + !boundary.equals("NO_SYNTHETIC"));
        if (boundary.equals("NO_PROFILE")) {
            configured = configured.withPropertyValues("im.bolt.client.mode=DEVELOPMENT_PLAINTEXT",
                    "im.bolt.client.tls.enabled=false", "im.bolt.client.development.synthetic-data=true",
                    "im.bolt.server.enabled=false");
        }
        if (boundary.equals("OUTSIDE_BIND") || boundary.equals("CONTAINER_UNDECLARED")) {
            configured = configured.withPropertyValues("im.bolt.server.enabled=true",
                    "im.bolt.server.mode=DEVELOPMENT_PLAINTEXT", "im.bolt.server.tls.enabled=false",
                    "im.bolt.server.development.synthetic-data=true", "im.bolt.server.host="
                            + (boundary.equals("OUTSIDE_BIND") ? "0.0.0.0" : "172.30.88.10"));
        }
        configured.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
            String expected = switch (boundary) {
                case "PRODUCTION", "NO_PROFILE" -> "requires bolt-dev without production profile";
                case "NO_SYNTHETIC" -> "requires synthetic data";
                default -> "development address";
            };
            assertThat(context.getStartupFailure()).hasStackTraceContaining(expected);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AccountBrokerProperties.class)
    static class TypedProperties {
    }
    @ParameterizedTest
    @ValueSource(strings = {"NEW_KEY", "NEW_ENV", "OLD_KEY", "OLD_ENV"})
    void removedPrefixOverridesCannotChangeFixedBrokerIdentities(String source) {
        String key = "im.account.broker." + (source.startsWith("OLD")
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
        AccountBrokerProperties properties = org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bindOrCreate("im.account.broker", AccountBrokerProperties.class);
        BoltTarget fixed = new BoltTarget("127.0.0.1:18020", "spiffe://im-chat/broker/broker-a");
        properties.setBootstrapTargets(List.of(fixed));
        DiscoveryClient discovery = org.mockito.Mockito.mock(DiscoveryClient.class);
        org.mockito.Mockito.when(discovery.getInstances(ServiceName.IM_BROKER.value())).thenReturn(List.of(
                new DefaultServiceInstance("broker-a", ServiceName.IM_BROKER.value(), "127.0.0.1", 18020, false)));
        BrokerClusterState committed = new BrokerClusterState(1L, 1L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-b", "127.0.0.1:18021",
                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        List<BoltTarget> invokedTargets = new ArrayList<>();
        BoltInvoker invoker = new BoltInvoker() {
            @Override
            public <T, R> R invoke(BoltTarget target, String service, String operation,
                                   T request, Class<R> responseType, int timeoutMillis) {
                invokedTargets.add(target);
                return responseType == BrokerClusterState.class ? responseType.cast(committed) : null;
            }
        };
        BrokerClient client = new BrokerClient(invoker, discovery, ServiceName.IM_BROKER,
                BrokerLoadBalance.HASH, 1000, properties.getBootstrapTargets());
        assertThat(client.currentClusterState()).isEqualTo(committed);
        client.registerBroker(new BrokerRegisterParams("broker-b", "127.0.0.1", 18021));
        assertThat(invokedTargets).containsExactly(fixed,
                new BoltTarget("127.0.0.1:18021", "spiffe://im-chat/broker/broker-b"));
        properties.setBootstrapTargets(List.of(new BoltTarget(fixed.address(), custom + "broker-a")));
        assertThatThrownBy(() -> new BrokerClient(invoker, discovery, ServiceName.IM_BROKER,
                BrokerLoadBalance.HASH, 1000, properties.getBootstrapTargets()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fixed prefix");
        assertThat(invokedTargets).hasSize(2);
    }

}
