package com.co.kc.imchat.broker.domain.cluster.model;

import java.io.Serializable;

/** 共识中的执行身份和排空事实；不表示本机拥有执行能力。 */
public record BrokerMembershipPermit(
        /* 许可唯一身份。 */
        String permitId,
        /* 所属成员操作。 */
        ClusterOperationId operationId,
        /* 所属成员步骤。 */
        String stepId,
        /* 登记时固定的配置目标。 */
        ConsensusMembership target,
        /* 执行原生配置的 Broker。 */
        BrokerId executorBrokerId,
        /* 执行进程轮次。 */
        String executorIncarnation,
        /* 许可排空结果。 */
        Result result) implements Serializable {
    /** 本机执行结果；UNKNOWN 表示尚未完成排空。 */
    public enum Result { UNKNOWN, TARGET_APPLIED, TARGET_NOT_APPLIED }

    /** 校验许可身份和结果字段，保证许可可以安全重放。 */
    public BrokerMembershipPermit {
        if (permitId == null || permitId.isBlank() || operationId == null || stepId == null || stepId.isBlank()
                || target == null || executorBrokerId == null || executorIncarnation == null
                || executorIncarnation.isBlank() || result == null) {
            throw new IllegalArgumentException("membership permit is incomplete");
        }
    }

    /** 判断许可是否已经产生终态排空结果。 */
    public boolean drained() { return result != Result.UNKNOWN; }

    /** 将未完成许可确认成唯一且不可矛盾的终态。 */
    public BrokerMembershipPermit confirm(Result outcome) {
        if (outcome == null || outcome == Result.UNKNOWN || (drained() && result != outcome)) {
            throw new IllegalArgumentException("membership permit result is unknown or contradictory");
        }
        return new BrokerMembershipPermit(permitId, operationId, stepId, target, executorBrokerId,
                executorIncarnation, outcome);
    }
}
