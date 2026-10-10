package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;

import java.time.Duration;
import java.time.Instant;

/**
 * 核验故障恢复证据并登记完成意图的共识命令。
 * 日志固定本轮恢复等待策略和判断时间，各节点按操作创建时间计算同一期限。
 */
public record VerifyRouteRecoveryCommand(
        /* 已登记故障操作。 */ ClusterOperationId operationId,
        /* 操作身份摘要。 */ CommandDigest commandDigest,
        /* 从操作创建起等待 Gateway ACK 的策略时长。 */ Duration recoveryTimeout,
        /* 本轮验证采用的确定时间。 */ Instant changedAt) implements BrokerClusterConsensusCommand {
    public VerifyRouteRecoveryCommand {
        if (operationId == null || commandDigest == null || recoveryTimeout == null || changedAt == null
                || recoveryTimeout.isNegative() || recoveryTimeout.isZero()) {
            throw new IllegalArgumentException("route recovery verification is invalid");
        }
    }
}
