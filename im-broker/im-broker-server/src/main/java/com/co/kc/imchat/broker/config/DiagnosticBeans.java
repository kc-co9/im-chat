package com.co.kc.imchat.broker.config;

import com.co.kc.imchat.broker.config.properties.BrokerManagementProperties;
import com.co.kc.imchat.broker.support.diagnostic.tracker.impl.ConnectionMigrationTracker;
import com.co.kc.imchat.broker.support.diagnostic.tracker.impl.GossipSyncTracker;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker 进程内诊断记录能力配置。
 */
@Configuration
public class DiagnosticBeans {

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

    @Bean
    public ConnectionMigrationTracker connectionMigrationTracker(
            BrokerManagementProperties properties
    ) {
        return new ConnectionMigrationTracker(properties.getHistoryCapacity());
    }
}
