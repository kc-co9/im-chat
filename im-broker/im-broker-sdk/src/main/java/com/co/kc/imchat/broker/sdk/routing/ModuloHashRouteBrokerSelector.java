package com.co.kc.imchat.broker.sdk.routing;

import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;

import java.util.Comparator;
import java.util.List;

/**
 * 使用稳定用户哈希对排序后的 ACTIVE Broker 取模。
 */
public final class ModuloHashRouteBrokerSelector implements RouteBrokerSelector {

    @Override
    public BrokerNode select(long userId, BrokerClusterState clusterState) {
        if (clusterState.algorithm() != BrokerSelectionAlgorithm.MODULO_HASH) {
            throw new IllegalArgumentException("cluster state algorithm is not MODULO_HASH");
        }
        List<BrokerNode> activeBrokers = clusterState.activeBrokers().stream()
                .sorted(Comparator.comparing(BrokerNode::brokerId))
                .toList();
        int index = (int) Long.remainderUnsigned(RouteBrokerHashing.userHash(userId),
                activeBrokers.size());
        return activeBrokers.get(index);
    }
}
