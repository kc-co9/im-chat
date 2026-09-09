package com.co.kc.imchat.service.account.infrastructure.config.beans;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.account.adapter.SessionConnectionAdapter;
import com.co.kc.imchat.service.account.infrastructure.config.properties.AccountBrokerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Account 外部能力适配器 Bean 装配。
 */
@Configuration
@EnableConfigurationProperties(AccountBrokerProperties.class)
public class AdapterBeans {

    @Bean
    @ConditionalOnMissingBean
    public BrokerClient brokerClient(BoltInvoker boltInvoker,
                                     DiscoveryClient discoveryClient,
                                     AccountBrokerProperties properties) {
        return new BrokerClient(
                boltInvoker,
                discoveryClient,
                ServiceName.IM_BROKER,
                BrokerLoadBalance.HASH,
                properties.getTimeoutMillis());
    }

    @Bean
    public SessionConnectionAdapter sessionConnectionAdapter(BrokerClient brokerClient) {
        return new SessionConnectionAdapter(brokerClient);
    }
}
