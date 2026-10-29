package com.co.kc.imchat.plugin.bolt.client;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.plugin.bolt.ImBoltAutoConfiguration;

import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BoltDevelopmentModeTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ImBoltAutoConfiguration.class)
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("bolt-dev"));

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"NO_PROFILE", "PRODUCTION", "NO_SYNTHETIC", "TLS_CONFLICT", "MODE_CONFLICT", "BIND_ANY", "CONTAINER_UNDECLARED"})
    void rejectsUnsafeDevelopmentAssemblyBeforeNativeStartup(String boundary) {
        ApplicationContextRunner configured = runner;
        if (boundary.equals("NO_PROFILE")) { configured = new ApplicationContextRunner().withUserConfiguration(ImBoltAutoConfiguration.class); }
        if (boundary.equals("PRODUCTION")) { configured = configured.withInitializer(context -> context.getEnvironment().setActiveProfiles("bolt-dev", "production")); }
        configured.withPropertyValues("im.bolt.client.enabled=true", "im.bolt.server.enabled=true",
                        "im.bolt.client.mode=DEVELOPMENT_PLAINTEXT", "im.bolt.server.mode=" + (boundary.equals("MODE_CONFLICT") ? "MTLS" : "DEVELOPMENT_PLAINTEXT"),
                        "im.bolt.client.allowed-peer-uri-prefixes[0]=spiffe://test/peer/",
                        "im.bolt.client.development.synthetic-data=" + !boundary.equals("NO_SYNTHETIC"),
                        "im.bolt.server.development.synthetic-data=true", "im.bolt.client.tls.enabled=" + boundary.equals("TLS_CONFLICT"),
                        "im.bolt.server.host=" + (boundary.equals("BIND_ANY") ? "0.0.0.0" : boundary.equals("CONTAINER_UNDECLARED") ? "172.30.88.2" : "127.0.0.1"),
                        "im.bolt.server.port=0")
                .run(context -> { assertThat(context).hasFailed(); assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class); });
    }

    @Test
    void fixedAddressPolicyRejectsDnsAndOtherPrivateNetworksWithoutCreatingConnections() {
        ImBoltProperties.Client policy = new ImBoltProperties.Client();
        policy.setMode(BoltTransportMode.DEVELOPMENT_PLAINTEXT);
        policy.getDevelopment().setSyntheticData(true);
        policy.setAllowedPeerUriPrefixes(List.of("spiffe://test/peer/"));
        BoltControlledClient client = new BoltControlledClient(policy);
        try {
            client.startup();
            for (String host : List.of("localhost", "10.0.0.1", "192.168.1.1", "172.30.88.2", "8.8.8.8")) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.acquire(new BoltTarget(host + ":12200", "spiffe://test/peer/dev"), 1000))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("development address");
            }
            assertThat(client.getAllManagedConnections()).isEmpty();
            policy.getDevelopment().setIsolatedContainerNetwork(true);
            com.co.kc.imchat.plugin.bolt.security.BoltDevelopmentValidator.validateAddress("172.30.88.2", policy.getDevelopment());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> com.co.kc.imchat.plugin.bolt.security.BoltDevelopmentValidator.validateAddress("172.30.89.2", policy.getDevelopment()))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally { client.shutdown(); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void productionServerRequiresTlsAndMutualClientAuthentication(boolean tlsWithoutClientAuth) {
        runner.withPropertyValues("im.bolt.server.enabled=true", "im.bolt.server.host=127.0.0.1", "im.bolt.server.port=0",
                        "im.bolt.server.tls.enabled=" + tlsWithoutClientAuth, "im.bolt.server.tls.need-client-auth=false")
                .run(context -> { assertThat(context).hasFailed(); assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class).hasStackTraceContaining("requires TLS and client authentication"); });
    }

    @Test
    void rejectsExternalDevelopmentClientUnderDeclaredMtlsProductionPolicy() {
        ImBoltProperties.Client actual = new ImBoltProperties.Client();
        actual.setMode(BoltTransportMode.DEVELOPMENT_PLAINTEXT);
        actual.setAllowedPeerUriPrefixes(List.of("spiffe://test/peer/"));
        actual.getDevelopment().setSyntheticData(true);
        BoltControlledClient external = new BoltControlledClient(actual);
        try {
            external.startup();
            new ApplicationContextRunner().withUserConfiguration(ImBoltAutoConfiguration.class)
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"))
                    .withPropertyValues("im.bolt.client.enabled=true", "im.bolt.client.mode=MTLS")
                    .withBean(RpcClient.class, () -> external, definition -> definition.setDestroyMethodName(""))
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasMessageContaining("actual client mode does not match");
                        assertThat(external.isRunningControlledAssembly()).isTrue();
                    });
        } finally { external.shutdown(); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MISSING_PROFILE", "PRODUCTION", "NO_CLIENT_AUTH", "BIND_MISMATCH"})
    void serverOnlyExternalBeanCannotBypassStartupPolicy(String boundary) {
        boolean development = !boundary.equals("NO_CLIENT_AUTH");
        RpcServer external = new RpcServer(boundary.equals("BIND_MISMATCH") ? "0.0.0.0" : "127.0.0.1", 0);
        external.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_ENABLE, false);
        external.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, false);
        ApplicationContextRunner configured = new ApplicationContextRunner().withUserConfiguration(ImBoltAutoConfiguration.class);
        if (boundary.equals("PRODUCTION")) { configured = configured.withInitializer(context -> context.getEnvironment().setActiveProfiles("production", "bolt-dev")); }
        if (boundary.equals("BIND_MISMATCH")) { configured = configured.withInitializer(context -> context.getEnvironment().setActiveProfiles("bolt-dev")); }
        configured.withPropertyValues("im.bolt.server.enabled=true", "im.bolt.server.host=127.0.0.1", "im.bolt.server.port=0",
                        "im.bolt.server.mode=" + (development ? "DEVELOPMENT_PLAINTEXT" : "MTLS"),
                        "im.bolt.server.development.synthetic-data=true")
                .withBean(RpcServer.class, () -> external, definition -> definition.setDestroyMethodName(""))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isInstanceOf(IllegalArgumentException.class);
                    assertThat(external.isStarted()).isFalse();
                });
    }

    @Test
    void matchingExternalControlledServerRemainsOwnedByItsSupplier() {
        ImBoltProperties.Server actual = new ImBoltProperties.Server();
        actual.setHost("127.0.0.1"); actual.setPort(0);
        actual.setMode(BoltTransportMode.DEVELOPMENT_PLAINTEXT);
        actual.getDevelopment().setSyntheticData(true);
        com.co.kc.imchat.plugin.bolt.server.BoltControlledServer external = new com.co.kc.imchat.plugin.bolt.server.BoltControlledServer(actual);
        try {
            assertThat(external.start()).isTrue();
            runner.withPropertyValues("im.bolt.server.enabled=true", "im.bolt.server.host=127.0.0.1", "im.bolt.server.port=0",
                            "im.bolt.server.mode=DEVELOPMENT_PLAINTEXT", "im.bolt.server.development.synthetic-data=true")
                    .withBean(RpcServer.class, () -> external, definition -> definition.setDestroyMethodName(""))
                    .run(context -> { assertThat(context).hasNotFailed(); assertThat(external.isStarted()).isTrue(); });
            assertThat(external.isStarted()).isTrue();
            runner.withPropertyValues("im.bolt.server.enabled=true", "im.bolt.server.host=::1", "im.bolt.server.port=0",
                            "im.bolt.server.mode=DEVELOPMENT_PLAINTEXT", "im.bolt.server.development.synthetic-data=true")
                    .withBean(RpcServer.class, () -> external, definition -> definition.setDestroyMethodName(""))
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasMessageContaining("actual server mode or binding");
                    });
            assertThat(external.isStarted()).isTrue();
        } finally { external.stop(); }
    }

    @Test
    void ipv6LoopbackUsesActualControlledDevelopmentTransport() {
        runner.withPropertyValues("im.bolt.client.enabled=true", "im.bolt.server.enabled=true",
                        "im.bolt.client.mode=DEVELOPMENT_PLAINTEXT", "im.bolt.server.mode=DEVELOPMENT_PLAINTEXT",
                        "im.bolt.client.allowed-peer-uri-prefixes[0]=spiffe://test/peer/",
                        "im.bolt.client.development.synthetic-data=true", "im.bolt.server.development.synthetic-data=true",
                        "im.bolt.server.host=::1", "im.bolt.server.port=0")
                .withBean(BoltRequestHandler.class, () -> new BoltRequestHandler() {
                    public String service() { return "ipv6"; }
                    public String operation() { return "ping"; }
                    public Object handle(BoltRequestContext context, String payload) {
                        assertThat(context.remoteHost()).isEqualTo("0:0:0:0:0:0:0:1");
                        assertThat(context.tlsVerified()).isFalse();
                        return "ipv6-pong";
                    }
                }).run(context -> {
                    assertThat(context).hasNotFailed();
                    RpcServer server = context.getBean(RpcServer.class);
                    server.registerUserProcessor(context.getBean(com.co.kc.imchat.plugin.bolt.server.BoltRequestProcessor.class));
                    assertThat(context.getBean(BoltInvoker.class).invoke(new BoltTarget("[::1]:" + server.port(), "spiffe://test/peer/dev"),
                            "ipv6", "ping", "request", String.class, 3000)).isEqualTo("ipv6-pong");
                });
    }

    @Test
    void ipv6PolicyDoesNotExpandTrustToMappedScopedOrNamedAddresses() {
        ImBoltProperties.Development development = new ImBoltProperties.Development();
        development.setSyntheticData(true);
        for (String rejected : List.of("::ffff:127.0.0.1", "::1%lo0", "[::1%lo0]", "localhost", "0:0:0:0:0:0:0:2")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> com.co.kc.imchat.plugin.bolt.security.BoltDevelopmentValidator.validateAddress(rejected, development))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("development address");
        }
    }

    @Test
    void explicitDevelopmentAssemblyUsesControlledClientAndNeverClaimsTlsIdentity() {
        AtomicReference<BoltRequestContext> received = new AtomicReference<>();
        runner.withPropertyValues("im.bolt.client.enabled=true", "im.bolt.server.enabled=true",
                        "im.bolt.client.mode=DEVELOPMENT_PLAINTEXT", "im.bolt.server.mode=DEVELOPMENT_PLAINTEXT",
                        "im.bolt.client.allowed-peer-uri-prefixes[0]=spiffe://test/peer/",
                        "im.bolt.client.development.synthetic-data=true", "im.bolt.server.development.synthetic-data=true",
                        "im.bolt.server.host=127.0.0.1", "im.bolt.server.port=0")
                .withBean(BoltRequestHandler.class, () -> new BoltRequestHandler() {
                    public String service() { return "dev"; }
                    public String operation() { return "ping"; }
                    public Object handle(BoltRequestContext context, String payload) { received.set(context); return "pong"; }
                }).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RpcClient.class)).isInstanceOf(BoltControlledClient.class);
                    RpcServer server = context.getBean(RpcServer.class);
                    server.registerUserProcessor(context.getBean(com.co.kc.imchat.plugin.bolt.server.BoltRequestProcessor.class));
                    assertThat(context.getBean(BoltInvoker.class).invoke(new BoltTarget("127.0.0.1:" + server.port(), "spiffe://test/peer/dev-routing-binding"),
                            "dev", "ping", "request", String.class, 3000)).isEqualTo("pong");
                    assertThat(received.get().tlsVerified()).isFalse();
                    assertThat(received.get().peerIdentity()).isNull();
                    assertThat(received.get().peerUriIdentities()).isEmpty();
                });
    }
}
