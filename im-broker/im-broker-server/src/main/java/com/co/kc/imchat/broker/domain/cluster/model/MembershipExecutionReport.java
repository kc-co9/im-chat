package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * 领域值对象：执行确认报告及已校验的通信身份，完整参与本机确认去重。
 * 报告不是已提交事实；聚合仍须结合当前许可重新校验。
 */
public record MembershipExecutionReport(
        /* 集群身份。 */ BrokerClusterId clusterId,
        /* 报告发送方。 */ BrokerId callerBrokerId,
        /* 本次接收方。 */ BrokerId receiverBrokerId,
        /* 成员操作。 */ ClusterOperationId operationId,
        /* 执行步骤。 */ String stepId,
        /* 执行许可。 */ String permitId,
        /* 执行节点。 */ BrokerId executorBrokerId,
        /* 执行进程。 */ String executorIncarnation,
        /* 固定目标摘要。 */ String targetDigest,
        /* 报告结果。 */ BrokerMembershipPermit.Result result,
        /* 来自受信上下文的节点身份。 */ BrokerId authenticatedExecutorBrokerId) {
}
