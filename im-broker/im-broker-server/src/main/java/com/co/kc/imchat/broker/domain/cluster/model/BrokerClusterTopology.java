package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;

import java.util.List;
import java.util.Map;

/** 已提交业务拓扑的值快照；不含 phase/ACK revision。 */
public record BrokerClusterTopology(
        /* 当前已提交的路由代次。 */
        RoutingEpoch epoch,
        /* 已提交的 Broker 节点快照。 */
        List<BrokerNode> brokers,
        /* Broker 到共识成员端点的绑定快照。 */
        Map<BrokerId, MembershipEndpoint> endpoints) {
    /** 复制节点和端点映射，避免调用方修改聚合内部状态。 */
    public BrokerClusterTopology {
        brokers = brokers.stream().map(BrokerNode::copy).toList();
        endpoints = Map.copyOf(endpoints);
    }

    /** 返回再次复制的节点列表，保证快照读取仍然是隔离的。 */
    @Override
    public List<BrokerNode> brokers() {
        return brokers.stream().map(BrokerNode::copy).toList();
    }
}
