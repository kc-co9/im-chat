package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.application.diagnostic.BrokerDiagnosticAppService;
import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerManagementProperties;
import com.co.kc.imchat.broker.adapter.diagnostic.GossipSyncTracker;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker 进程内诊断记录能力配置。
 */
@Configuration
public class DiagnosticConfiguration {

    @Bean
    public BrokerDiagnosticAppService brokerDiagnosticAppService(
            BrokerRegistry brokers, GatewayRegistry gateways, ConnectionRegistry connections,
            GossipSyncTracker tracker, BrokerProperties properties) {
        return new BrokerDiagnosticAppService(brokers, gateways, connections, tracker,
                properties.getInstance().getId(), properties.getInstance().getAddress());
    }

    @Bean
    public SmartInitializingSingleton brokerManagementPortValidator(
            BrokerManagementProperties properties,
            ImBoltProperties boltProperties
    ) {
        return () -> properties.validateBoltPort(boltProperties.getServer().getPort());
    }

    @Bean
    public GossipSyncTracker gossipSyncTracker(BrokerManagementProperties properties) {
        return new GossipSyncTracker(properties.getHistoryCapacity());
    }

}
