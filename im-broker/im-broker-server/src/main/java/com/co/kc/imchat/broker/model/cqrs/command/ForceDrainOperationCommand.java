package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;

/**
 * 经显式批准后强制结束已失败 drain operation 的共识命令。
 */
public record ForceDrainOperationCommand(ClusterOperationId operationId,
                               String commandDigest,
                               ClusterRevision expectedRevision,
                               String reason,
                               long changedAtEpochMilli)
        implements BrokerClusterConsensusCommand {
    public ForceDrainOperationCommand {
        if (operationId == null || commandDigest == null || commandDigest.isBlank()
                || expectedRevision == null || reason == null || reason.isBlank()
                || changedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("force broker drain change is invalid");
        }
    }
}
