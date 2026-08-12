package com.co.kc.imchat.plugin.dubbo;

import com.co.kc.imchat.plugin.dubbo.properties.ImDubboProperties;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImDubboAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.application.name=im-test")
            .withConfiguration(AutoConfigurations.of(ImDubboAutoConfiguration.class));

    private final ImDubboAutoConfiguration autoConfiguration = new ImDubboAutoConfiguration();

    @Test
    void providesSafeDubboBeansWhenRegistryIsDisabledForTests() {
        ImDubboProperties properties = properties("N/A", null, "DUBBO_GROUP");

        ApplicationConfig application = autoConfiguration.dubboApplicationConfig(
                new MockEnvironment().withProperty("spring.application.name", "im-test"));
        ProtocolConfig protocol = autoConfiguration.dubboProtocolConfig(properties);
        RegistryConfig registry = autoConfiguration.dubboRegistryConfig(properties);
        ConsumerConfig consumer = autoConfiguration.dubboConsumerConfig(properties);

        assertThat(application.getName()).isEqualTo("im-test");
        assertThat(protocol.getName()).isEqualTo("dubbo");
        assertThat(protocol.getPort()).isEqualTo(-1);
        assertThat(registry.getAddress()).isEqualTo("N/A");
        assertThat(consumer.isCheck()).isFalse();
    }

    @Test
    void configuresNacosRegistry() {
        RegistryConfig registryConfig = autoConfiguration.dubboRegistryConfig(
                properties("nacos://127.0.0.1:8848", "namespace-id", "DUBBO_GROUP"));

        assertThat(registryConfig.getAddress()).isEqualTo("nacos://127.0.0.1:8848");
        assertThat(registryConfig.getGroup()).isEqualTo("DUBBO_GROUP");
        assertThat(registryConfig.getParameters()).containsEntry("namespace", "namespace-id");
    }

    @Test
    void rejectsBlankRegistryAddress() {
        assertThatThrownBy(() -> autoConfiguration.dubboRegistryConfig(
                properties(" ", null, "DUBBO_GROUP")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("im.dubbo.registry.address must not be blank");
    }

    @Test
    void doesNotConfigureDubboWhenDisabled() {
        contextRunner
                .withPropertyValues("im.dubbo.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ApplicationConfig.class);
                    assertThat(context).doesNotHaveBean(RegistryConfig.class);
                });
    }

    private ImDubboProperties properties(String address, String namespace, String group) {
        ImDubboProperties properties = new ImDubboProperties();
        properties.getRegistry().setAddress(address);
        properties.getRegistry().setNamespace(namespace);
        properties.getRegistry().setGroup(group);
        return properties;
    }
}
