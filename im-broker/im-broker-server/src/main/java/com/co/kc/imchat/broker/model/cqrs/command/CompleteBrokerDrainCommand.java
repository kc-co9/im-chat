package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;

/**
 * 在全部 drain 证据满足后将目标 Broker 置为 INACTIVE 的共识命令。
 */
public record CompleteBrokerDrainCommand(ClusterOperationId operationId,
                                  String commandDigest,
                                  ClusterRevision expectedRevision,
                                  long changedAtEpochMilli)
        implements BrokerClusterConsensusCommand {
    public CompleteBrokerDrainCommand {
        if (operationId == null || commandDigest == null || commandDigest.isBlank()
                || expectedRevision == null || changedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("complete broker drain change is invalid");
        }
    }
}
