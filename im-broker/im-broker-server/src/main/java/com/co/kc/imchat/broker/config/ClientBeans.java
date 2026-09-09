package com.co.kc.imchat.broker.config;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.domain.store.BrokerStateStore;
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
public class ClientBeans {

    @Bean
    @ConditionalOnMissingBean
    public GatewayClient gatewayClient(BoltInvoker boltInvoker,
                                       BrokerProperties properties) {
        return new GatewayClient(boltInvoker, properties.getGatewayPush().getBolt().getTimeoutMillis());
    }

    @Bean
    @ConditionalOnMissingBean
    public GossipPeerClient gossipPeerClient(BoltInvoker boltInvoker) {
        return new GossipPeerClient(boltInvoker);
    }

    @Bean
    @ConditionalOnMissingBean
    public GossipSynchronizer gossipSynchronizer(BrokerStateStore brokerStateStore,
                                                 GossipPeerClient gossipPeerClient) {
        return new GossipSynchronizer(brokerStateStore, gossipPeerClient);
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
