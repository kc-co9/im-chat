package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.application.gossip.BrokerGossipAppService;
import com.co.kc.imchat.broker.adapter.gossip.BrokerGossipAdapter;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerGossipScheduler;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.model.properties.BrokerGossipSettings;
import com.co.kc.imchat.broker.support.gossip.GossipSyncTracker;
import com.co.kc.imchat.plugin.gossip.sync.GossipSynchronizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配注册表事件的 Gossip 观察更新用例，监听入口由接口层注册。 */
@Configuration
public class RegistryGossipConfiguration {
    @Bean
    public BrokerGossipSettings brokerGossipSettings(
            ClusterProperties clusterProperties, BrokerProperties brokerProperties) {
        BrokerProperties.Instance instance = brokerProperties.getInstance();
        return new BrokerGossipSettings(instance.getId(), instance.getAddress(),
                clusterProperties.getSeedAddresses(), clusterProperties.getTrustedPeers().stream()
                        .map(peer -> new BrokerGossipSettings.PeerBinding(peer.getObservationId(), peer.getTarget())).toList(),
                clusterProperties.getGossipFanout(),
                java.time.Duration.ofMillis(clusterProperties.getGossipTimeoutMs()));
    }

    @Bean
    public BrokerGossipScheduler brokerGossipScheduler(
            BrokerRegistry brokerRegistry, GossipSynchronizer gossipSynchronizer,
            BrokerGossipSettings gossipProperties, GossipSyncTracker gossipSyncTracker) {
        return new BrokerGossipScheduler(brokerRegistry, gossipSynchronizer, gossipProperties, gossipSyncTracker);
    }

    @Bean
    public BrokerGossipAppService brokerGossipAppService(
            BrokerRegistry brokerRegistry, GatewayRegistry gatewayRegistry, BrokerGossipAdapter stateStore) {
        return new BrokerGossipAppService(brokerRegistry, gatewayRegistry, stateStore);
    }
}
