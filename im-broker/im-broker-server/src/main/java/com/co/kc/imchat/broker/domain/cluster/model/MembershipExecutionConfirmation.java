package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * 领域值对象：上报某次成员变更执行已结束及其结果。
 * 合法性由聚合结合当前执行记录判断，不将上报内容视为已确认事实。
 */
public record MembershipExecutionConfirmation(
        /* 所属操作。 */ ClusterOperationId operationId,
        /* 操作步骤。 */ String stepId,
        /* 本次执行许可。 */ String permitId,
        /* 执行节点。 */ BrokerId executorBrokerId,
        /* 执行进程身份。 */ String executorIncarnation,
        /* 固定目标摘要。 */ String targetDigest,
        /* 上报执行结果。 */ BrokerMembershipPermit.Result result) {
}
