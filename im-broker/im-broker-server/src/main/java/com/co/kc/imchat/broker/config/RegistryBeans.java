package com.co.kc.imchat.broker.config;

import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.broker.memory.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.GatewayRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.memory.InMemoryGatewayRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker 注册表实现配置。
 */
@Configuration
public class RegistryBeans {

    @Bean
    @ConditionalOnMissingBean(ConnectionRegistry.class)
    public ConnectionRegistry inMemoryConnectionRegistry() {
        return new InMemoryConnectionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(BrokerRegistry.class)
    public BrokerRegistry inMemoryBrokerRegistry() {
        return new InMemoryBrokerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(GatewayRegistry.class)
    public GatewayRegistry inMemoryGatewayRegistry() {
        return new InMemoryGatewayRegistry();
    }
}
