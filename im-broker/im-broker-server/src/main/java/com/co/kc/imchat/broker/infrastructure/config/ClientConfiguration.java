package com.co.kc.imchat.broker.infrastructure.config;

import com.alipay.remoting.rpc.RpcClient;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.adapter.gossip.BrokerGossipAdapter;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.client.BoltRpcClient;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
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
                                       BrokerProperties properties, RpcClient client) {
        if (!(client instanceof BoltControlledClient controlled)
                || !controlled.isRunningControlledAssembly()
                || !(boltInvoker instanceof BoltClientInvoker owned)
                || !owned.usesControlledClient(controlled)) {
            throw new IllegalArgumentException("Gateway client requires same running controlled Bolt assembly");
        }
        return new GatewayClient(boltInvoker, properties.getGatewayPush().getBolt().getTimeoutMs(),
                controlled.configuredMode(),
                properties.getGatewayPush().getBolt().getDevelopmentTargets());
    }

    @Bean
    @ConditionalOnMissingBean
    public GossipPeerClient gossipPeerClient(BoltInvoker boltInvoker, ImBoltProperties properties) {
        return new GossipPeerClient(new BoltRpcClient(boltInvoker, properties.getClient()));
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
