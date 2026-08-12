package com.co.kc.imchat.gateway.ws.config;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.gateway.ws.config.properties.GatewayProperties;
import com.co.kc.imchat.gateway.ws.handler.FrameWriteHandler;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.server.NettyWebSocketServer;
import com.co.kc.imchat.gateway.ws.security.authentication.WsAuthenticationManager;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.session.token.TokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
            GatewayProperties properties) {
        return new BrokerClient(boltInvoker, properties.getBroker().getBolt().getAddress(),
                properties.getBroker().getBolt().getLoadBalance(),
                properties.getBroker().getBolt().getTimeoutMillis());
    }

    @Bean
    @ConditionalOnMissingBean
    public WsAuthenticationManager wsAuthenticationManager(TokenService tokenService) {
        return new WsAuthenticationManager(tokenService);
    }

    @Bean
    public FrameWriteHandler frameWriteBoltHandler(ConnectionRegistry connectionRegistry) {
        return new FrameWriteHandler(connectionRegistry);
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
