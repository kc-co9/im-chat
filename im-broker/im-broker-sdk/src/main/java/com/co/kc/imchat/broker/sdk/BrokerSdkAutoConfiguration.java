package com.co.kc.imchat.broker.sdk;

import com.co.kc.imchat.broker.sdk.lifecycle.BrokerRefresher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(BrokerClient.class)
public class BrokerSdkAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public BrokerRefresher brokerRefresher(BrokerClient brokerClient) {
        return new BrokerRefresher(brokerClient);
    }
}
