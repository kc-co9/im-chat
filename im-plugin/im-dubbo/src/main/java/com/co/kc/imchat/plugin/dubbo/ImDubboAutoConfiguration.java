package com.co.kc.imchat.plugin.dubbo;

import com.co.kc.imchat.plugin.dubbo.aspect.RpcExceptionAspect;
import com.co.kc.imchat.plugin.dubbo.properties.ImDubboProperties;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Map;

@AutoConfiguration
@ConditionalOnProperty(prefix = "im.dubbo", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableDubbo(scanBasePackages = "com.co.kc.imchat")
@EnableConfigurationProperties(ImDubboProperties.class)
public class ImDubboAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RpcExceptionAspect rpcExceptionAspect() {
        return new RpcExceptionAspect();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApplicationConfig dubboApplicationConfig(Environment environment) {
        ApplicationConfig config = new ApplicationConfig();
        config.setName(environment.getProperty("spring.application.name", "im-chat"));
        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public ProtocolConfig dubboProtocolConfig(ImDubboProperties properties) {
        ProtocolConfig config = new ProtocolConfig();
        config.setName(properties.getProtocol().getName());
        config.setPort(properties.getProtocol().getPort());
        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public RegistryConfig dubboRegistryConfig(ImDubboProperties dubboProperties) {
        ImDubboProperties.Registry registry = dubboProperties.getRegistry();
        Assert.hasText(registry.getAddress(), "im.dubbo.registry.address must not be blank");
        RegistryConfig config = new RegistryConfig();
        config.setAddress(registry.getAddress());
        config.setGroup(registry.getGroup());
        if (StringUtils.hasText(registry.getNamespace())) {
            config.setParameters(Map.of("namespace", registry.getNamespace()));
        }
        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public ConsumerConfig dubboConsumerConfig(ImDubboProperties properties) {
        ConsumerConfig config = new ConsumerConfig();
        config.setTimeout(properties.getConsumer().getTimeout());
        config.setCheck(properties.getConsumer().isCheck());
        return config;
    }
}
