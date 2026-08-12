package com.co.kc.imchat.broker.lifecycle;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.plugin.gossip.sync.GossipSyncOperations;
import com.co.kc.imchat.plugin.gossip.sync.GossipSynchronizer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Broker 集群 Gossip 同步生命周期。
 * <p>
 * 定时从配置的种子节点和本地已知 Broker 列表中选择部分 peer，通过 digest/delta 交换注册表变更，
 * 让 Broker、Gateway、用户路由等最终一致状态在集群内扩散。
 */
@Slf4j
@Component
public class BrokerGossipLifecycle {
    private static final long GOSSIP_DELAY_MILLIS = 5000L;
    private static final GossipSyncOperations SYNC_OPERATIONS = new GossipSyncOperations(
            BrokerBoltOperation.GOSSIP_DIGEST.service().service(),
            BrokerBoltOperation.GOSSIP_DIGEST.operation(),
            BrokerBoltOperation.GOSSIP_DELTA.operation());

    private final BrokerRegistry brokerRegistry;
    private final GossipSynchronizer gossipSynchronizer;
    private final ClusterProperties clusterProperties;
    private final BrokerProperties brokerProperties;

    public BrokerGossipLifecycle(BrokerRegistry brokerRegistry,
                                 GossipSynchronizer gossipSynchronizer,
                                 ClusterProperties clusterProperties,
                                 BrokerProperties brokerProperties) {
        this.brokerRegistry = brokerRegistry;
        this.gossipSynchronizer = gossipSynchronizer;
        this.clusterProperties = clusterProperties;
        this.brokerProperties = brokerProperties;
    }

    /**
     * 周期性选择一批 peer 发起 Gossip 同步。
     */
    @Scheduled(fixedDelay = GOSSIP_DELAY_MILLIS)
    public void gossip() {
        List<String> peers = new ArrayList<>(peers());
        if (peers.isEmpty()) {
            return;
        }
        Collections.shuffle(peers);
        peers.stream().limit(clusterProperties.getGossipFanout()).forEach(this::syncPeer);
    }

    /**
     * 与单个 peer 交换状态摘要和缺失的增量数据。
     */
    private void syncPeer(String address) {
        try {
            gossipSynchronizer.syncPeer(
                    brokerProperties.getInstance().getId(),
                    address,
                    SYNC_OPERATIONS,
                    clusterProperties.getGossipTimeoutMillis());
        } catch (RuntimeException ex) {
            log.warn("failed to gossip broker state to peer:{}, error:{}", address, ex.toString());
        }
    }

    /**
     * 优先使用已发现的 Broker；尚未发现远端 Broker 时，使用静态种子节点引导入群。
     */
    private List<String> peers() {
        String localAddress = brokerProperties.getInstance().getAddress();
        List<String> peers = brokerRegistry.list()
                .stream()
                .map(BrokerEndpointDTO::address)
                .distinct()
                .filter(address -> !localAddress.equals(address))
                .toList();
        if (CollectionUtils.isEmpty(peers)) {
            peers = clusterProperties.getSeedAddresses()
                    .stream()
                    .distinct()
                    .filter(address -> !localAddress.equals(address))
                    .toList();
        }
        return peers;
    }

}
