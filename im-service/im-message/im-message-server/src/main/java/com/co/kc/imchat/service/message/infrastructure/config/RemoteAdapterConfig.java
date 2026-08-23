package com.co.kc.imchat.service.message.infrastructure.config;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import com.co.kc.imchat.service.message.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.service.social.facade.SocialService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "im.message.remote-adapter", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BrokerProperties.class)
public class RemoteAdapterConfig {

    @DubboReference(interfaceClass = SocialService.class, version = "1.0.0")
    private SocialService socialService;

    @Bean
    @ConditionalOnMissingBean
    public BrokerClient brokerClient(BoltInvoker boltInvoker,
                                     DiscoveryClient discoveryClient,
                                     BrokerProperties properties) {
        return new BrokerClient(boltInvoker,
                discoveryClient,
                ServiceName.IM_BROKER,
                BrokerLoadBalance.HASH,
                properties.getTimeoutMillis());
    }

    @Bean
    @ConditionalOnMissingBean
    public SocialAdapter socialAdapter() {
        return new SocialAdapter(socialService);
    }
}
