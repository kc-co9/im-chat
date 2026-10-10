package com.co.kc.imchat.broker.model.cqrs.command;

import java.io.Serializable;

/**
 * BrokerCluster 可确定性应用的封闭共识命令。
 *
 * <p>每个实现只携带一种业务意图所需的数据，避免使用 nullable 通用命令绕过聚合不变量。</p>
 */
public sealed interface BrokerClusterConsensusCommand extends Serializable
        permits BeginBrokerClusterOperationCommand, CommitBrokerOperationCommand,
        AdvanceClusterOperationCommand, CommitRoutingEpochFenceCommand,
        UpdateBrokerDrainProgressCommand, CompleteBrokerDrainCommand, ForceDrainOperationCommand,
        VerifyRouteRecoveryCommand, CompleteRouteRecoveryCommand {
}
