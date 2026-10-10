package com.co.kc.imchat.broker.sdk.cluster;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SDK 与 Broker 原子安装的权威 Broker 集群状态。
 *
 * @param revision  任意内容变化时递增的状态修订号
 * @param epoch     RouteBroker 选择输入变化时递增的路由代次
 * @param algorithm 建群时确定且不可在线修改的选择算法
 * @param brokers   权威 Broker 节点快照
 */
public record BrokerClusterState(
        long revision,
        long epoch,
        BrokerSelectionAlgorithm algorithm,
        List<BrokerNode> brokers) implements Serializable {

    public BrokerClusterState {
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (epoch < 0L) {
            throw new IllegalArgumentException("epoch must not be negative");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm must not be null");
        }
        if (brokers == null) {
            throw new IllegalArgumentException("brokers must not be null");
        }
        brokers = brokers.stream()
                .sorted(Comparator.comparing(BrokerNode::brokerId))
                .toList();
        Set<String> brokerIds = new HashSet<>();
        boolean hasActiveBroker = false;
        for (BrokerNode broker : brokers) {
            if (broker == null) {
                throw new IllegalArgumentException("broker must not be null");
            }
            if (!brokerIds.add(broker.brokerId())) {
                throw new IllegalArgumentException("duplicate brokerId: " + broker.brokerId());
            }
            hasActiveBroker |= broker.participatesInRouting();
        }
        if (!hasActiveBroker) {
            throw new IllegalArgumentException("cluster state must contain at least one ACTIVE broker");
        }
    }

    public List<BrokerNode> activeBrokers() {
        return brokers.stream().filter(BrokerNode::participatesInRouting).toList();
    }

}
