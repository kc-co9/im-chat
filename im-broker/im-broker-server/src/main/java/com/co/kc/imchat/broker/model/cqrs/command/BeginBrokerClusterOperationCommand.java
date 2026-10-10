package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationProposal;

/**
 * 校验并持久化集群操作事实，不携带目标拓扑，也不修改 Broker 节点状态。
 */
public record BeginBrokerClusterOperationCommand(BrokerClusterOperationProposal proposal)
        implements BrokerClusterConsensusCommand {

    public BeginBrokerClusterOperationCommand {
        if (proposal == null) {
            throw new IllegalArgumentException("broker cluster operation proposal is required");
        }
    }
}
