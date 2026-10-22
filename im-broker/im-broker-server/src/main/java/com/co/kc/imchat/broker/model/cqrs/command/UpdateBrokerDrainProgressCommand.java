package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

import java.time.Instant;

/**
 * 提交 drain 过程中 Gateway 确认或路由重建摘要的共识命令。
 */
public record UpdateBrokerDrainProgressCommand(ClusterOperationId operationId,
                                        String commandDigest,
                                        DrainProgressUpdate progressUpdate,
                                        Instant changedAt)
        implements BrokerClusterConsensusCommand {
    public UpdateBrokerDrainProgressCommand {
        if (operationId == null || commandDigest == null || commandDigest.isBlank()
                || progressUpdate == null || changedAt == null || changedAt.isBefore(Instant.EPOCH)) {
            throw new IllegalArgumentException("broker drain progress update is invalid");
        }
        changedAt = Instant.ofEpochMilli(changedAt.toEpochMilli());
    }
}
