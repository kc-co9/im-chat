package com.co.kc.imchat.plugin.nacos;

import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import com.alibaba.cloud.nacos.registry.NacosRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

class ImNacosAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ImNacosAutoConfiguration.class));

    @Test
    void failsWhenDiscoveryIsEnabledWithoutRegistrationInfrastructure() {
        contextRunner.run(context -> assertThat(context.getStartupFailure())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nacos registration infrastructure"));
    }

    @Test
    void allowsMissingRegistrationInfrastructureWhenDiscoveryIsDisabled() {
        contextRunner
                .withPropertyValues("spring.cloud.nacos.discovery.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void doesNotCreateListenerWhenNacosDiscoveryIsDisabled() {
        contextRunner
                .withPropertyValues("spring.cloud.nacos.discovery.enabled=false")
                .withUserConfiguration(EnabledRegistrationConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean("nonWebNacosRegistrationListener"));
    }

    @Test
    void allowsMissingRegistrationInfrastructureWhenCloudDiscoveryIsDisabled() {
        contextRunner
                .withPropertyValues("spring.cloud.discovery.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsWhenServiceRegistrationIsDisabled() {
        contextRunner
                .withUserConfiguration(DisabledRegistrationConfiguration.class)
                .run(context -> assertThat(context.getStartupFailure())
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("register-enabled"));
    }

    @Configuration(proxyBeanMethods = false)
    static class DisabledRegistrationConfiguration {

        @Bean
        NacosAutoServiceRegistration nacosAutoServiceRegistration() {
            return mock(NacosAutoServiceRegistration.class);
        }

        @Bean
        NacosRegistration nacosRegistration() {
            NacosRegistration registration = mock(NacosRegistration.class);
            when(registration.isRegisterEnabled()).thenReturn(false);
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EnabledRegistrationConfiguration {

        @Bean
        NacosAutoServiceRegistration nacosAutoServiceRegistration() {
            return mock(NacosAutoServiceRegistration.class);
        }

        @Bean
        NacosRegistration nacosRegistration() {
            NacosRegistration registration = mock(NacosRegistration.class);
            when(registration.isRegisterEnabled()).thenReturn(true);
            return registration;
        }
    }
}
