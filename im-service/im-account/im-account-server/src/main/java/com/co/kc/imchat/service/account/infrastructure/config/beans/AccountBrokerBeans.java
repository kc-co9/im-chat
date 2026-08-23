package com.co.kc.imchat.service.account.infrastructure.config.beans;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.account.adapter.broker.SessionConnectionAdapter;
import com.co.kc.imchat.service.account.infrastructure.config.properties.AccountBrokerProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "im.account.provider", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AccountBrokerProperties.class)
public class AccountBrokerBeans {

    @Bean
    @ConditionalOnMissingBean
    public BrokerClient accountBrokerClient(BoltInvoker boltInvoker,
                                            DiscoveryClient discoveryClient,
                                            AccountBrokerProperties properties) {
        return new BrokerClient(boltInvoker,
                discoveryClient,
                ServiceName.IM_BROKER,
                com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance.HASH,
                properties.getTimeoutMillis());
    }

    @Bean
    public SessionConnectionAdapter sessionConnectionAdapter(BrokerClient brokerClient) {
        return new SessionConnectionAdapter(brokerClient);
    }

}
