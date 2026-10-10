package com.co.kc.imchat.broker.sdk.routing;

import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;

import java.util.List;

/**
 * 使用等权最高随机权重哈希选择 RouteBroker。
 */
public final class RendezvousHashRouteBrokerSelector implements RouteBrokerSelector {

    @Override
    public BrokerNode select(long userId, BrokerClusterState clusterState) {
        if (clusterState.algorithm() != BrokerSelectionAlgorithm.RENDEZVOUS_HASH) {
            throw new IllegalArgumentException("cluster state algorithm is not RENDEZVOUS_HASH");
        }
        List<BrokerNode> activeBrokers = clusterState.activeBrokers();
        BrokerNode selected = activeBrokers.get(0);
        long selectedScore = RouteBrokerHashing.score(userId, selected.brokerId());
        for (int index = 1; index < activeBrokers.size(); index++) {
            BrokerNode candidate = activeBrokers.get(index);
            long candidateScore = RouteBrokerHashing.score(userId, candidate.brokerId());
            int scoreComparison = Long.compareUnsigned(candidateScore, selectedScore);
            if (scoreComparison > 0 || (scoreComparison == 0
                    && candidate.brokerId().compareTo(selected.brokerId()) < 0)) {
                selected = candidate;
                selectedScore = candidateScore;
            }
        }
        return selected;
    }
}
