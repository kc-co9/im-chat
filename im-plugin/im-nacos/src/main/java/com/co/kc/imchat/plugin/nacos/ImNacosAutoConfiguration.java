package com.co.kc.imchat.plugin.nacos;

import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import com.alibaba.cloud.nacos.registry.NacosRegistration;
import com.alibaba.cloud.nacos.registry.NacosServiceRegistryAutoConfiguration;
import com.co.kc.imchat.plugin.nacos.listener.NonWebNacosRegistrationListener;
import com.co.kc.imchat.plugin.nacos.validation.NacosRegistrationValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Nacos 公共扩展配置。
 */
@AutoConfiguration
@AutoConfigureAfter(NacosServiceRegistryAutoConfiguration.class)
@ConditionalOnProperty(prefix = "spring.cloud.discovery", name = "enabled", matchIfMissing = true)
public class ImNacosAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "spring.cloud.nacos.discovery", name = "enabled", matchIfMissing = true)
    NacosRegistrationValidator nacosRegistrationValidator(
            ObjectProvider<NacosAutoServiceRegistration> autoRegistrationProvider,
            ObjectProvider<NacosRegistration> registrationProvider) {
        return new NacosRegistrationValidator(autoRegistrationProvider, registrationProvider);
    }

    @Bean
    @ConditionalOnBean(NacosAutoServiceRegistration.class)
    @ConditionalOnProperty(prefix = "spring.cloud.nacos.discovery", name = "enabled", matchIfMissing = true)
    NonWebNacosRegistrationListener nonWebNacosRegistrationListener(
            NacosAutoServiceRegistration registration, NacosRegistration nacosRegistration) {
        return new NonWebNacosRegistrationListener(registration, nacosRegistration);
    }
}
