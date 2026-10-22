package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import com.co.kc.imchat.broker.interfaces.lifecycle.BrokerRegistrationLifecycle;
import com.co.kc.imchat.broker.interfaces.scheduler.GatewayCleanupScheduler;
import com.co.kc.imchat.broker.application.local.LocalGatewayCleanupAppService;
import com.co.kc.imchat.broker.adapter.gossip.BrokerGossipAdapter;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.RegistryProperties;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.broker.infrastructure.domain.cluster.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.BatchConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryBatchConnectionRegistry;
import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;
import java.time.Duration;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker 注册表实现配置。
 */
@Configuration
public class RegistryConfiguration {

    @Bean
    public BrokerRegistrationLifecycle brokerRegistrationLifecycle(
            BrokerRegistry brokerRegistry, DomainEventPublisher eventPublisher, BrokerProperties properties) {
        BrokerProperties.Instance instance = properties.getInstance();
        return new BrokerRegistrationLifecycle(brokerRegistry, eventPublisher,
                instance.getId(), instance.getHost(), instance.getPort());
    }

    @Bean
    public LocalGatewayCleanupAppService localGatewayCleanupAppService(
            GatewayRegistry gatewayRegistry, ConnectionRegistry connectionRegistry,
            BrokerGossipAdapter stateStore, RegistryProperties registryProperties,
            BrokerRoutingProperties routingProperties) {
        return new LocalGatewayCleanupAppService(gatewayRegistry, connectionRegistry, stateStore,
                registryProperties.getGatewayTtl(), routingProperties.getRouteTombstoneGracePeriod());
    }

    @Bean
    public GatewayCleanupScheduler gatewayCleanupScheduler(
            LocalGatewayCleanupAppService cleanupService) {
        return new GatewayCleanupScheduler(cleanupService);
    }

    @Bean
    @ConditionalOnMissingBean(ConnectionRegistry.class)
    public BatchConnectionRegistry inMemoryConnectionRegistry(BrokerRoutingProperties properties, JetCacheFactory cacheFactory) {
        return new InMemoryBatchConnectionRegistry(new InMemoryConnectionRegistry(), properties,
                cacheFactory.local("broker.route.pending-batches", properties.getSnapshotBatchTimeout(),
                        properties.getMaxPendingSnapshotBatches()),
                cacheFactory.local("broker.route.cleanup-checkpoints", Duration.ofDays(3650),
                        properties.getDrainCleanupResultCapacity()));
    }

    @Bean
    @ConditionalOnMissingBean(BrokerRegistry.class)
    public BrokerRegistry inMemoryBrokerRegistry() {
        return new InMemoryBrokerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(GatewayRegistry.class)
    public GatewayRegistry inMemoryGatewayRegistry() {
        return new InMemoryGatewayRegistry();
    }
}
