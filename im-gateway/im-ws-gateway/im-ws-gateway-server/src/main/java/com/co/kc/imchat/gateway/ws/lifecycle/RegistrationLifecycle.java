package com.co.kc.imchat.gateway.ws.lifecycle;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionSyncParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.gateway.ws.config.properties.GatewayProperties;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * WS 网关注册生命周期。
 * <p>
 * 应用启动完成后向 Broker 注册当前网关，并定时刷新本机活跃连接列表。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = GatewayProperties.PREFIX + ".register", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RegistrationLifecycle {
    private static final long REGISTER_REFRESH_DELAY_MILLIS = 30_000L;

    private final String gatewayId;
    private final String host;
    private final int boltPort;
    private final BrokerClient brokerClient;
    private final ConnectionRegistry connectionRegistry;
    private volatile boolean registered;

    @Autowired
    public RegistrationLifecycle(GatewayProperties properties,
                                 ImBoltProperties boltProperties,
                                 BrokerClient brokerClient,
                                 ConnectionRegistry connectionRegistry) {
        this.gatewayId = properties.gatewayId(boltProperties.getServer().getPort());
        this.host = properties.getBolt().getHost();
        this.boltPort = boltProperties.getServer().getPort();
        this.brokerClient = brokerClient;
        this.connectionRegistry = connectionRegistry;
    }

    public RegistrationLifecycle(String gatewayId, String host, int boltPort,
                                 BrokerClient brokerClient, ConnectionRegistry connectionRegistry) {
        this.gatewayId = gatewayId;
        this.host = host;
        this.boltPort = boltPort;
        this.brokerClient = brokerClient;
        this.connectionRegistry = connectionRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            register();
            syncConnections();
        } catch (RuntimeException ex) {
            log.warn("failed to register ws gateway, will retry later, error:{}", ex.toString());
        }
    }

    @Scheduled(fixedDelay = REGISTER_REFRESH_DELAY_MILLIS)
    public void refresh() {
        try {
            refreshGatewayRegistration();
            syncConnections();
        } catch (RuntimeException ex) {
            log.warn("failed to refresh ws gateway registration, will retry later, error:{}", ex.toString());
        }
    }

    private void register() {
        brokerClient.registerGateway(new GatewayRegisterParams(gatewayId, host, boltPort));
        brokerClient.refreshBrokerAddresses();
        registered = true;
    }

    private void refreshGatewayRegistration() {
        if (registered) {
            heartbeat();
            return;
        }
        register();
    }

    private void heartbeat() {
        brokerClient.heartbeatGateway(new GatewayHeartbeatParams(gatewayId));
        brokerClient.refreshBrokerAddresses();
    }

    private void syncConnections() {
        brokerClient.syncConnections(
                new ConnectionSyncParams(gatewayId, connectionRegistry.activeUserIds()));
    }
}
