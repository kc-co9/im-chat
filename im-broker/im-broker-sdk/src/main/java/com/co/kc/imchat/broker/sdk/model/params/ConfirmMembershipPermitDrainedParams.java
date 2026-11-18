package com.co.kc.imchat.broker.sdk.model.params;

import java.io.Serializable;

/** 精确许可的排空事实。远程来源必须由 MTLS Broker 身份独立认证。 */
/**
 * Broker 成员许可排空确认请求，携带完整身份链和不可变目标摘要。
 */
public record ConfirmMembershipPermitDrainedParams(
        /* 集群身份。 */
        String clusterId,
        /* 发起确认的 Broker。 */
        String callerBrokerId,
        /* 接收确认的 Broker。 */
        String receiverBrokerId,
        /* 成员操作身份。 */
        String operationId,
        /* 成员步骤身份。 */
        String stepId,
        /* 本次执行许可身份。 */
        String permitId,
        /* 实际执行原生配置的 Broker。 */
        String executorBrokerId,
        /* 执行进程轮次。 */
        String executorIncarnation,
        /* 固定目标配置摘要。 */
        String targetDigest,
        /* 原生配置执行结果。 */
        Result result) implements Serializable {
    /** 原生配置调用结束后观察到的目标结果。 */
    public enum Result { TARGET_APPLIED, TARGET_NOT_APPLIED }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 以具名字段组合单次确认请求；build 复用规范构造校验，不修改已构建请求。
     */
    public static final class Builder {
        private String clusterId;
        private String callerBrokerId;
        private String receiverBrokerId;
        private String operationId;
        private String stepId;
        private String permitId;
        private String executorBrokerId;
        private String executorIncarnation;
        private String targetDigest;
        private Result result;

        private Builder() {
        }

        public Builder clusterId(String value) {
            clusterId = value;
            return this;
        }

        public Builder callerBrokerId(String value) {
            callerBrokerId = value;
            return this;
        }

        public Builder receiverBrokerId(String value) {
            receiverBrokerId = value;
            return this;
        }

        public Builder operationId(String value) {
            operationId = value;
            return this;
        }

        public Builder stepId(String value) {
            stepId = value;
            return this;
        }

        public Builder permitId(String value) {
            permitId = value;
            return this;
        }

        public Builder executorBrokerId(String value) {
            executorBrokerId = value;
            return this;
        }

        public Builder executorIncarnation(String value) {
            executorIncarnation = value;
            return this;
        }

        public Builder targetDigest(String value) {
            targetDigest = value;
            return this;
        }

        public Builder result(Result value) {
            result = value;
            return this;
        }

        public ConfirmMembershipPermitDrainedParams build() {
            return new ConfirmMembershipPermitDrainedParams(clusterId, callerBrokerId, receiverBrokerId,
                    operationId, stepId, permitId, executorBrokerId, executorIncarnation, targetDigest, result);
        }
    }

    /** 校验跨 Broker 传递所需的身份字段，避免不完整事实进入共识边界。 */
    public ConfirmMembershipPermitDrainedParams {
        for (String value : new String[]{clusterId, callerBrokerId, receiverBrokerId, operationId,
                stepId, permitId, executorBrokerId, executorIncarnation, targetDigest}) {
            if (value == null || value.isBlank()) { throw new IllegalArgumentException("permit confirmation identity is incomplete"); }
        }
        if (result == null) { throw new IllegalArgumentException("permit confirmation requires a known result"); }
    }
}
