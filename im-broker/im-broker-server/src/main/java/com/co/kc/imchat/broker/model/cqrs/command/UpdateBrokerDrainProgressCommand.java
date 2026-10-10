package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

/**
 * 提交 drain 过程中 Gateway 确认或路由重建摘要的共识命令。
 */
public record UpdateBrokerDrainProgressCommand(ClusterOperationId operationId,
                                        String commandDigest,
                                        DrainProgressUpdate progressUpdate,
                                        long changedAtEpochMilli)
        implements BrokerClusterConsensusCommand {
    public UpdateBrokerDrainProgressCommand {
        if (operationId == null || commandDigest == null || commandDigest.isBlank()
                || progressUpdate == null || changedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("broker drain progress update is invalid");
        }
    }
}
