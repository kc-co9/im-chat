package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 领域结果：认领状态及唯一执行者的本轮尝试，不表示共识已经登记。
 */
public record LearnerConfirmationClaim(
        /* 是否可以执行，或已有相同／冲突请求占用。 */ Status status,
        /* 仅 ACQUIRED 时存在。 */ LearnerConfirmationAttempt executionAttempt) {
    public LearnerConfirmationClaim {
        AssertUtils.domainPropNotNull("learner claim status is required", status);
        AssertUtils.domainPropTrue("learner claim attempt does not match status",
                (status == Status.ACQUIRED) == (executionAttempt != null));
    }

    public enum Status {
        ACQUIRED,
        IN_PROGRESS,
        CONFLICT
    }
}
