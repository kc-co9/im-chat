package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;

/** 确认日志提交结果；UNKNOWN 只允许重投原事实，不授权重新执行配置。 */
public record MembershipPermitDrainedResult(
        /* 确认请求的处理状态。 */
        Status status,
        /* 非 Leader 时提供的跟随地址。 */
        String leaderEndpoint,
        /* 面向调用方的结果说明。 */
        String message) implements Serializable {
    public static MembershipPermitDrainedResult identityMismatch() {
        return rejected("confirmation identity mismatch");
    }

    public static MembershipPermitDrainedResult rejected(String reason) {
        return new MembershipPermitDrainedResult(Status.REJECTED, null, reason);
    }

    /** 排空事实提交到共识层后的处理状态。 */
    public enum Status { COMMITTED, DUPLICATE, NOT_LEADER, REJECTED, UNKNOWN }
}
