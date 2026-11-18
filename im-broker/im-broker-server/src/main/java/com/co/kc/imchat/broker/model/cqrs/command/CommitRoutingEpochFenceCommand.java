package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;

/**
 * 在权威集群状态中提交或恢复旧 epoch fence 的共识命令。
 */
public record CommitRoutingEpochFenceCommand(RoutingEpochFence operation) implements BrokerClusterConsensusCommand {
    public CommitRoutingEpochFenceCommand {
        if (operation == null) {
            throw new IllegalArgumentException("fencing operation is required");
        }
    }
}
