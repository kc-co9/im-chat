package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * 领域结果：执行报告的确认状态，不授权重新执行成员配置。
 * UNKNOWN只表示未确认，不能解释为未提交。
 */
public record MembershipConfirmationResult(
        /* 确认状态。 */ Status status,
        /* 非Leader时的寻址提示。 */ String leaderEndpoint,
        /* 具体处理原因。 */ String message) {
    public static MembershipConfirmationResult identityMismatch() {
        return rejected("confirmation identity mismatch");
    }

    public static MembershipConfirmationResult rejected(String reason) {
        return new MembershipConfirmationResult(Status.REJECTED, null, reason);
    }

    public enum Status { COMMITTED, DUPLICATE, NOT_LEADER, REJECTED, UNKNOWN }
}
