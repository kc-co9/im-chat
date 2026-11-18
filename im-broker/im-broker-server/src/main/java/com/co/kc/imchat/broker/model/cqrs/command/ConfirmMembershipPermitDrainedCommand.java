package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;

/** 持久化精确许可事实，不授予本机执行能力，不接受可覆盖的目标。 */
public record ConfirmMembershipPermitDrainedCommand(
        /* 成员操作身份。 */
        ClusterOperationId operationId,
        /* 成员步骤身份。 */
        String stepId,
        /* 执行许可身份。 */
        String permitId,
        /* 执行原生配置的 Broker。 */
        BrokerId executorBrokerId,
        /* 执行进程轮次。 */
        String executorIncarnation,
        /* 固定目标配置摘要。 */
        String targetDigest,
        /* 许可排空结果。 */
        BrokerMembershipPermit.Result result) implements BrokerClusterConsensusCommand {
    /** 校验共识命令所需的精确许可身份，禁止空值和未知结果。 */
    public ConfirmMembershipPermitDrainedCommand {
        if (operationId == null || stepId == null || stepId.isBlank() || permitId == null || permitId.isBlank()
                || executorBrokerId == null || executorIncarnation == null || executorIncarnation.isBlank()
                || targetDigest == null || targetDigest.isBlank() || result == null) {
            throw new IllegalArgumentException("membership permit command is incomplete");
        }
    }
}
