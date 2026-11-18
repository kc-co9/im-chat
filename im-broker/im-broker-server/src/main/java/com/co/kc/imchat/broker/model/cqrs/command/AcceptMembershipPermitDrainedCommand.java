package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 已认证 Broker 发送的许可排空事实，应用层只负责接收并持久化。 */
public record AcceptMembershipPermitDrainedCommand(
        /* 集群身份。 */
        String clusterId,
        /* 原始确认发送方。 */
        String callerBrokerId,
        /* 当前接收确认的 Broker。 */
        String receiverBrokerId,
        /* 成员操作身份。 */
        String operationId,
        /* 成员步骤身份。 */
        String stepId,
        /* 执行许可身份。 */
        String permitId,
        /* 事实中声明的执行 Broker。 */
        String executorBrokerId,
        /* 事实中声明的执行进程轮次。 */
        String executorIncarnation,
        /* 固定目标配置摘要。 */
        String targetDigest,
        /* 原生配置结果。 */
        Result result,
        /* 从受信认证上下文取得的执行 Broker。 */
        String authenticatedExecutorBrokerId) {
    /** 必填约束在命令构造边界建立；身份关系及授权仍由应用用例校验。 */
    public AcceptMembershipPermitDrainedCommand {
        AssertUtils.argNotBlank("clusterId is required", clusterId);
        AssertUtils.argNotBlank("callerBrokerId is required", callerBrokerId);
        AssertUtils.argNotBlank("receiverBrokerId is required", receiverBrokerId);
        AssertUtils.argNotBlank("operationId is required", operationId);
        AssertUtils.argNotBlank("stepId is required", stepId);
        AssertUtils.argNotBlank("permitId is required", permitId);
        AssertUtils.argNotBlank("executorBrokerId is required", executorBrokerId);
        AssertUtils.argNotBlank("executorIncarnation is required", executorIncarnation);
        AssertUtils.argNotBlank("targetDigest is required", targetDigest);
        AssertUtils.argNotNull("result is required", result);
        AssertUtils.argNotBlank("authenticatedExecutorBrokerId is required", authenticatedExecutorBrokerId);
    }

    /** 远端执行者观察到的原生配置结果。 */
    public enum Result { TARGET_APPLIED, TARGET_NOT_APPLIED }
}
