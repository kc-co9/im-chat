package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import java.util.concurrent.CompletionStage;

/**
 * 领域结果：成员请求认领状态与共享的本轮领域结果。
 */
public record MembershipOperationClaim(
        /* 是否新执行、共享或拒绝。 */ Status status,
        /* 仅 ACQUIRED 有执行尝试。 */ MembershipOperationAttempt executionAttempt,
        /* 新执行或已有结果的只读视图。 */ CompletionStage<MembershipOperationOutcome> resultView) {
    public enum Status {
        ACQUIRED, SHARED, CONFLICT, CAPACITY_EXCEEDED
    }

    public MembershipOperationClaim {
        AssertUtils.domainPropNotNull("membership claim status is required", status);
        AssertUtils.domainPropNotNull("membership result view is required", resultView);
        AssertUtils.domainPropTrue("membership claim attempt does not match status",
                (status == Status.ACQUIRED) == (executionAttempt != null));
    }
}
