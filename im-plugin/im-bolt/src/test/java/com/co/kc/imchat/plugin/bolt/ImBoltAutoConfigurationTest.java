package com.co.kc.imchat.plugin.bolt;

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
    void bindsServerPort() {
        contextRunner
                .withPropertyValues("im.bolt.server.port=12345")
                .run(context -> {
                    ImBoltProperties properties = context.getBean(ImBoltProperties.class);

                    assertThat(properties.getServer().getPort()).isEqualTo(12345);
                });
    }
}
