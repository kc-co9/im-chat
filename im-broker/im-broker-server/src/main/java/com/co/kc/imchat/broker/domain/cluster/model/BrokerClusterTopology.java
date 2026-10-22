package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;

import java.util.List;
import java.util.Map;

/** 已提交拓扑与实际共识配置的值快照；不含 phase/ACK revision。 */
public record BrokerClusterTopology(
        RoutingEpoch epoch,
        List<BrokerNode> brokers,
        Map<BrokerId, MembershipEndpoint> endpoints,
        ConsensusMembership membership) {
    public BrokerClusterTopology {
        brokers = brokers.stream().map(BrokerNode::copy).toList();
        endpoints = Map.copyOf(endpoints);
    }

    @Override
    public List<BrokerNode> brokers() {
        return brokers.stream().map(BrokerNode::copy).toList();
    }
}
