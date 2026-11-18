package com.co.kc.imchat.broker.sdk;

import com.co.kc.imchat.broker.sdk.lifecycle.BrokerRefresher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 在已提供 BrokerClient 时装配集群状态刷新组件，允许调用方覆盖默认实现。 */
@AutoConfiguration
@ConditionalOnBean(BrokerClient.class)
public class BrokerSdkAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public BrokerRefresher brokerRefresher(BrokerClient brokerClient) {
        return new BrokerRefresher(brokerClient);
    }
}
