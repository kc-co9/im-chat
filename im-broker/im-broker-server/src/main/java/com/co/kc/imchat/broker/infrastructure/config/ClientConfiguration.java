package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.adapter.gossip.BrokerGossipAdapter;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.gossip.client.GossipPeerClient;
import com.co.kc.imchat.plugin.gossip.sync.GossipSynchronizer;
import com.co.kc.imchat.service.message.facade.MessageService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 外部客户端配置。
 */
@Configuration
public class ClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GatewayClient gatewayClient(BoltInvoker boltInvoker,
                                       BrokerProperties properties) {
        return new GatewayClient(boltInvoker, properties.getGatewayPush().getBolt().getTimeoutMs());
    }

    @Bean
    @ConditionalOnMissingBean
    public GossipPeerClient gossipPeerClient(BoltInvoker boltInvoker) {
        return new GossipPeerClient(boltInvoker);
    }

    @Bean
    @ConditionalOnMissingBean
    public GossipSynchronizer gossipSynchronizer(BrokerGossipAdapter brokerGossipAdapter,
                                                 GossipPeerClient gossipPeerClient) {
        return new GossipSynchronizer(brokerGossipAdapter, gossipPeerClient);
    }

    @Configuration
    static class MessageServiceReferenceBeans {

        @DubboReference(interfaceClass = MessageService.class, version = "1.0.0")
        private MessageService messageService;

        @Bean
        @ConditionalOnMissingBean
        public MessageService messageService() {
            return messageService;
        }
    }
}
