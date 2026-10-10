package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;

/** 请求状态机应用已登记 operation 的实体行为，不携带目标拓扑。 */
public record CommitBrokerOperationCommand(
        /* 已登记操作的稳定幂等身份。 */
        ClusterOperationId operationId,
        /* 本次提交必须匹配的命令摘要。 */
        String commandDigest,
        /* Begin 接收时冻结的 revision。 */
        ClusterRevision expectedRevision,
        /* 本次提交的领域事实时间。 */
        long changedAtEpochMilli) implements BrokerClusterConsensusCommand {

    public CommitBrokerOperationCommand {
        if (operationId == null || commandDigest == null || commandDigest.isBlank()
                || expectedRevision == null || changedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("broker operation commit is invalid");
        }
    }
}
