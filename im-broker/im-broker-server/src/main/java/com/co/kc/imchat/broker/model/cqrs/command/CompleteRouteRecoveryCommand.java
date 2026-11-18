package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;

import java.time.Instant;

/**
 * 完成故障路由恢复的共识命令。
 * 不携带目标终态或降级原因，聚合依据当前已持久化意图选择 SUCCEEDED 或 DEGRADED。
 */
public record CompleteRouteRecoveryCommand(
        /* 已验证故障操作。 */ ClusterOperationId operationId,
        /* 操作身份摘要。 */ CommandDigest commandDigest,
        /* 本轮完成采用的确定时间。 */ Instant changedAt) implements BrokerClusterConsensusCommand {
    public CompleteRouteRecoveryCommand {
        if (operationId == null || commandDigest == null || changedAt == null) {
            throw new IllegalArgumentException("route recovery completion is invalid");
        }
    }
}
