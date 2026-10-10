package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.application.gossip.RegistryGossipAppService;
import com.co.kc.imchat.broker.adapter.gossip.BrokerStateStore;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerGossipScheduler;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerGossipProperties;
import com.co.kc.imchat.broker.adapter.diagnostic.GossipSyncTracker;
import com.co.kc.imchat.plugin.gossip.sync.GossipSynchronizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配注册表事件的 Gossip 观察更新用例，监听入口由接口层注册。 */
@Configuration
public class RegistryGossipConfiguration {
    @Bean
    public BrokerGossipScheduler brokerGossipScheduler(
            BrokerRegistry brokerRegistry, GossipSynchronizer gossipSynchronizer,
            ClusterProperties clusterProperties, BrokerProperties brokerProperties,
            GossipSyncTracker gossipSyncTracker) {
        BrokerProperties.Instance instance = brokerProperties.getInstance();
        return new BrokerGossipScheduler(brokerRegistry, gossipSynchronizer,
                new BrokerGossipProperties(instance.getId(), instance.getAddress(),
                        clusterProperties.getSeedAddresses(), clusterProperties.getGossipFanout(),
                        clusterProperties.getGossipTimeoutMillis()), gossipSyncTracker);
    }

    @Bean
    public RegistryGossipAppService registryGossipAppService(
            BrokerRegistry brokerRegistry, GatewayRegistry gatewayRegistry, BrokerStateStore stateStore) {
        return new RegistryGossipAppService(brokerRegistry, gatewayRegistry, stateStore);
    }
}
