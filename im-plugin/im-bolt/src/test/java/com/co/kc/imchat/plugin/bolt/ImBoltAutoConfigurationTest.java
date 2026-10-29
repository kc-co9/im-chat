package com.co.kc.imchat.plugin.bolt;

import com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ImBoltAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ImBoltAutoConfiguration.class);

    @Test
    void keepsClientAndServerDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(RpcClient.class);
            assertThat(context).doesNotHaveBean(RpcServer.class);
            assertThat(context.getBean(ImBoltProperties.class).getClient().isEnabled()).isFalse();
            assertThat(context.getBean(ImBoltProperties.class).getServer().isEnabled()).isFalse();
        });
    }

    @Test
    void explicitTypedPolicyRejectsMissingMaterialsAtStartup() {
        contextRunner.withPropertyValues("im.bolt.client.enabled=true",
                        "im.bolt.client.allowed-peer-uri-prefixes[0]=spiffe://test/peer/",
                        "im.bolt.client.tls.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("Bolt TLS key-store is required");
                });
    }

    @Test
    void disabledLegacyDefaultsDoNotRequireNewTypedMaterials() {
        contextRunner.withPropertyValues("im.bolt.client.mode=MTLS").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ImBoltProperties.class).getClient().getCreationThreads()).isEqualTo(2);
            assertThat(context.getBean(ImBoltProperties.class).getClient().getMaxInflightRpcs()).isEqualTo(64);
        });
    }

    @Test
    void rejectsUnsupportedClientOrExtensionConfiguration() throws Exception {
        try (BoltSecurityGateTestSupport materials = new BoltSecurityGateTestSupport()) {
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            String[] properties = controlledProperties(material);
            contextRunner.withPropertyValues(properties).withBean(RpcClient.class, RpcClient::new)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("owned controlled client and invoker assembly");
                    });
            contextRunner.withPropertyValues(properties).withBean(com.co.kc.imchat.plugin.bolt.spi.BoltInvoker.class,
                            () -> org.mockito.Mockito.mock(com.co.kc.imchat.plugin.bolt.spi.BoltInvoker.class))
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("owned controlled client and invoker assembly");
                    });
            com.co.kc.imchat.plugin.bolt.client.BoltControlledClient controlled = new com.co.kc.imchat.plugin.bolt.client.BoltControlledClient(
                    BoltSecurityGateTestSupport.policy(material));
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> controlled.option(
                        com.alipay.remoting.config.BoltClientOption.CONN_RECONNECT_SWITCH, true))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fixed");
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> controlled.setConnectionManager(
                        org.mockito.Mockito.mock(com.alipay.remoting.ConnectionManager.class)))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot be replaced");
            } finally { controlled.shutdown(); }
        }
    }

    @Test
    void startsWithSafeDefaultControlledAssemblyAndSupportedExternalBean() throws Exception {
        try (BoltSecurityGateTestSupport materials = new BoltSecurityGateTestSupport()) {
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            contextRunner.withPropertyValues(controlledProperties(material)).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(RpcClient.class)).isInstanceOf(com.co.kc.imchat.plugin.bolt.client.BoltControlledClient.class);
                assertThat(context.getBean(com.co.kc.imchat.plugin.bolt.spi.BoltInvoker.class))
                        .isInstanceOf(com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker.class);
            });
            contextRunner.withPropertyValues(controlledProperties(material))
                    .withBean(RpcClient.class, () -> {
                        com.co.kc.imchat.plugin.bolt.client.BoltControlledClient external =
                                new com.co.kc.imchat.plugin.bolt.client.BoltControlledClient(BoltSecurityGateTestSupport.policy(material));
                        external.startup();
                        return external;
                    }, definition -> definition.setDestroyMethodName("shutdown"))
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }

    @Test
    void rejectsUninitializedAndClosedExternalControlledBeansWithoutTakingOwnership() throws Exception {
        try (BoltSecurityGateTestSupport materials = new BoltSecurityGateTestSupport()) {
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            for (boolean stopped : java.util.List.of(false, true)) {
                com.co.kc.imchat.plugin.bolt.client.BoltControlledClient external =
                        new com.co.kc.imchat.plugin.bolt.client.BoltControlledClient(BoltSecurityGateTestSupport.policy(material));
                if (stopped) { external.startup(); external.shutdown(); }
                try {
                    contextRunner.withPropertyValues(controlledProperties(material)).withBean(RpcClient.class, () -> external,
                                    definition -> definition.setDestroyMethodName(""))
                            .run(context -> {
                                assertThat(context).hasFailed();
                                assertThat(context.getStartupFailure()).isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("running controlled assembly");
                            });
                    if (!stopped) { external.startup(); }
                } finally { external.shutdown(); }
            }
        }
    }

    private static String[] controlledProperties(BoltSecurityGateTestSupport.NativeMaterial material) {
        return new String[]{"im.bolt.client.enabled=true", "im.bolt.client.allowed-peer-uri-prefixes[0]=spiffe://test/peer/",
                "im.bolt.client.tls.enabled=true", "im.bolt.client.tls.key-store=" + material.store(),
                "im.bolt.client.tls.key-store-password=" + material.storePassword()};
    }

    @Test
    void bindsServerAddressAndPort() {
        contextRunner
                .withPropertyValues(
                        "im.bolt.server.host=127.0.0.1",
                        "im.bolt.server.port=12345")
                .run(context -> {
                    ImBoltProperties properties = context.getBean(ImBoltProperties.class);

                    assertThat(properties.getServer().getHost()).isEqualTo("127.0.0.1");
                    assertThat(properties.getServer().getPort()).isEqualTo(12345);
                    properties.getServer().getTls().setEnabled(true);
                    properties.getServer().getTls().setKeyStore("configuration-only-test.p12");
                    properties.getServer().getTls().setKeyStorePassword("test-only");
                    RpcServer rpcServer = new ImBoltAutoConfiguration().boltRpcServer(properties, context.getEnvironment());
                    assertThat(rpcServer.ip()).isEqualTo("127.0.0.1");
                    assertThat(rpcServer.port()).isEqualTo(12345);
                });
    }
}
