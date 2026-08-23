package com.co.kc.imchat.gateway.ws.config;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.gateway.ws.config.properties.GatewayProperties;
import com.co.kc.imchat.gateway.ws.handler.FrameWriteHandler;
import com.co.kc.imchat.gateway.ws.handler.ConnectionCloseHandler;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.server.NettyWebSocketServer;
import com.co.kc.imchat.gateway.ws.security.authentication.WsAuthenticationManager;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayBeans {

    @Bean
    @ConditionalOnMissingBean
    public ConnectionRegistry connectionRegistry() {
        return new ConnectionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public BrokerClient brokerClient(
            BoltInvoker boltInvoker,
            DiscoveryClient discoveryClient,
            GatewayProperties properties) {
        return new BrokerClient(boltInvoker,
                discoveryClient,
                ServiceName.IM_BROKER,
                properties.getBroker().getBolt().getLoadBalance(),
                properties.getBroker().getBolt().getTimeoutMillis());
    }

    @Bean
    public FrameWriteHandler frameWriteBoltHandler(ConnectionRegistry connectionRegistry) {
        return new FrameWriteHandler(connectionRegistry);
    }

    @Bean
    public ConnectionCloseHandler connectionCloseBoltHandler(ConnectionRegistry connectionRegistry) {
        return new ConnectionCloseHandler(connectionRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public NettyWebSocketServer nettyWebSocketServer(
            GatewayProperties properties,
            ImBoltProperties boltProperties,
            BrokerClient brokerClient,
            ConnectionRegistry connectionRegistry,
            WsAuthenticationManager authenticationManager) {
        return new NettyWebSocketServer(
                properties.getPort(),
                properties.gatewayId(boltProperties.getServer().getPort()),
                properties.getPath(),
                brokerClient,
                connectionRegistry, authenticationManager,
                properties.getIdle().getReaderIdleSeconds(), properties.getMaxFramePayloadLength());
    }
}
