package com.co.kc.imchat.gateway.ws.config;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import lombok.extern.slf4j.Slf4j;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.gateway.ws.config.properties.GatewayProperties;
import com.co.kc.imchat.gateway.ws.handler.FrameWriteHandler;
import com.co.kc.imchat.gateway.ws.handler.ConnectionCloseHandler;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.routing.GatewayInstanceIdentity;
import com.co.kc.imchat.gateway.ws.routing.GatewayRouteSnapshotCoordinator;
import com.co.kc.imchat.gateway.ws.delivery.FrameDeliveryDeduplicator;
import com.co.kc.imchat.gateway.ws.delivery.ConnectionCloseDeduplicator;
import com.co.kc.imchat.gateway.ws.security.GatewayBrokerAuthorizer;
import com.co.kc.imchat.gateway.ws.server.NettyWebSocketServer;
import com.co.kc.imchat.gateway.ws.security.authentication.WsAuthenticationManager;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
@Slf4j
public class GatewayBeans {

    @Bean
    @ConditionalOnMissingBean
    public ConnectionRegistry connectionRegistry(BrokerClient brokerClient, GatewayInstanceIdentity identity) {
        return new ConnectionRegistry(removal -> {
            if (!removal.mutation().routeAbsent()) {
                return;
            }
            try {
                brokerClient.unregisterConnection(new ConnectionUnregisterParams(
                        removal.userId(), identity.gatewayId(), removal.mutation().routeRevision()));
            } catch (RuntimeException failure) {
                log.warn("failed to unregister ws route, gatewayId:{}, userId:{}", identity.gatewayId(), removal.userId(), failure);
            }
        });
    }

    @Bean
    public GatewayInstanceIdentity gatewayInstanceIdentity(
            GatewayProperties properties, ImBoltProperties boltProperties) {
        String stableEndpoint = properties.getBolt().getHost()
                + "-" + boltProperties.getServer().getPort();
        return new GatewayInstanceIdentity(
                "gateway-" + stableEndpoint + "-" + UUID.randomUUID());
    }

    @Bean(destroyMethod = "close")
    public GatewayRouteSnapshotCoordinator gatewayRouteSnapshotCoordinator(
            GatewayInstanceIdentity identity,
            ConnectionRegistry connectionRegistry,
            BrokerClient brokerClient) {
        return new GatewayRouteSnapshotCoordinator(
                identity.gatewayId(), connectionRegistry, brokerClient);
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
                properties.getBroker().getBolt().getTimeoutMillis(),
                properties.getBroker().getBootstrapTargets());
    }

    @Bean
    public FrameDeliveryDeduplicator frameDeliveryDeduplicator(
            GatewayProperties properties) {
        return new FrameDeliveryDeduplicator(
                properties.getDelivery().getDeduplicationCapacity(),
                properties.getDelivery().getDeduplicationRetention(),
                System::currentTimeMillis);
    }

    @Bean
    public FrameWriteHandler frameWriteBoltHandler(
            ConnectionRegistry connectionRegistry,
            FrameDeliveryDeduplicator deduplicator,
            GatewayBrokerAuthorizer authorizer) {
        return new FrameWriteHandler(connectionRegistry, deduplicator, authorizer);
    }

    @Bean
    public ConnectionCloseDeduplicator connectionCloseDeduplicator(
            GatewayProperties properties) {
        return new ConnectionCloseDeduplicator(
                properties.getDelivery().getCloseDeduplicationCapacity(),
                properties.getDelivery().getCloseDeduplicationRetention(),
                System::currentTimeMillis);
    }

    @Bean
    public ConnectionCloseHandler connectionCloseBoltHandler(
            ConnectionRegistry connectionRegistry,
            ConnectionCloseDeduplicator deduplicator,
            GatewayBrokerAuthorizer authorizer) {
        return new ConnectionCloseHandler(connectionRegistry, deduplicator, authorizer);
    }

    @Bean
    public GatewayBrokerAuthorizer gatewayBrokerAuthorizer(
            BrokerClient brokerClient,
            GatewayProperties properties, ImBoltProperties boltProperties) {
        if (properties.getBroker().isAllowTrustedNetworkIdentity()
                && boltProperties.getServer().getMode() != BoltTransportMode.DEVELOPMENT_PLAINTEXT) {
            throw new IllegalArgumentException("Gateway trusted network identity requires Bolt development mode");
        }
        return new GatewayBrokerAuthorizer(brokerClient::currentClusterState,
                properties.getBroker().isAllowTrustedNetworkIdentity());
    }

    @Bean
    @ConditionalOnMissingBean
    public NettyWebSocketServer nettyWebSocketServer(
            GatewayProperties properties,
            ImBoltProperties boltProperties,
            BrokerClient brokerClient,
            ConnectionRegistry connectionRegistry,
            GatewayInstanceIdentity identity,
            WsAuthenticationManager authenticationManager) {
        return new NettyWebSocketServer(
                properties.getBindHost(),
                properties.getPort(),
                identity.gatewayId(),
                properties.getPath(),
                brokerClient,
                connectionRegistry, authenticationManager,
                properties.getIdle().getReaderIdleSeconds(), properties.getMaxFramePayloadLength());
    }
}
