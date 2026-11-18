package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;

/** 持久化精确许可事实，不授予本机执行能力，不接受可覆盖的目标。 */
public record RegisterMembershipPermitCommand(
        /* 成员操作身份。 */
        ClusterOperationId operationId,
        /* 成员步骤身份。 */
        String stepId,
        /* 新许可身份。 */
        String permitId,
        /* 执行原生配置的 Broker。 */
        BrokerId executorBrokerId,
        /* 执行进程轮次。 */
        String executorIncarnation,
        /* 调用方期望的集群修订版本。 */
        ClusterRevision expectedRevision) implements BrokerClusterConsensusCommand {
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 组合本次登记身份；每次 build 创建独立 record，并复用其规范构造校验。
     */
    public static final class Builder {
        private ClusterOperationId operationId;
        private String stepId;
        private String permitId;
        private BrokerId executorBrokerId;
        private String executorIncarnation;
        private ClusterRevision expectedRevision;

        private Builder() {
        }

        public Builder operationId(ClusterOperationId value) {
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

        public Builder executorBrokerId(BrokerId value) {
            executorBrokerId = value;
            return this;
        }

        public Builder executorIncarnation(String value) {
            executorIncarnation = value;
            return this;
        }

        public Builder expectedRevision(ClusterRevision value) {
            expectedRevision = value;
            return this;
        }

        public RegisterMembershipPermitCommand build() {
            return new RegisterMembershipPermitCommand(operationId, stepId, permitId,
                    executorBrokerId, executorIncarnation, expectedRevision);
        }
    }

    /** 校验登记命令的身份和期望版本，确保登记具备幂等依据。 */
    public RegisterMembershipPermitCommand {
        if (operationId == null || stepId == null || stepId.isBlank() || permitId == null || permitId.isBlank()
                || executorBrokerId == null || executorIncarnation == null || executorIncarnation.isBlank()
                || expectedRevision == null) {
            throw new IllegalArgumentException("membership permit command is incomplete");
        }
    }
}
