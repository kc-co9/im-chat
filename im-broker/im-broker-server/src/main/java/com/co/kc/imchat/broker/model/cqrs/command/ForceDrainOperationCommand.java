package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;

import java.time.Instant;

/**
 * 经显式批准后强制结束已失败 drain operation 的共识命令。
 */
public record ForceDrainOperationCommand(ClusterOperationId operationId,
                               String commandDigest,
                               ClusterRevision expectedRevision,
                               String reason,
                               Instant changedAt)
        implements BrokerClusterConsensusCommand {
    public ForceDrainOperationCommand {
        if (operationId == null || commandDigest == null || commandDigest.isBlank()
                || expectedRevision == null || reason == null || reason.isBlank()
                || changedAt == null || changedAt.isBefore(Instant.EPOCH)) {
            throw new IllegalArgumentException("force broker drain change is invalid");
        }
        changedAt = Instant.ofEpochMilli(changedAt.toEpochMilli());
    }
}
