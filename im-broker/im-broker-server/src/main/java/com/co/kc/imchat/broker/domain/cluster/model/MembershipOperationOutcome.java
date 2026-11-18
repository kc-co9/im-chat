package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * 领域结果：成员本地尝试的结束事实，不代表 operation 终态或许可排空。
 * Leader 提示和原因保持原值，不成为成员地址或授权事实。
 */
public record MembershipOperationOutcome(
        /* 本轮处理状态。 */ Status status,
        /* 已关联操作；未登记时可为空。 */ BrokerClusterOperation operation,
        /* 已知 Leader 提示；可为空。 */ String leaderEndpoint,
        /* 具体原因；可为空。 */ String message) {
    public enum Status {
        APPLIED, DUPLICATE, CONFLICT, REJECTED, NOT_LEADER, UNAVAILABLE, IN_PROGRESS
    }

    public boolean definitelyRejected() {
        return status == Status.REJECTED || status == Status.CONFLICT || status == Status.NOT_LEADER;
    }
}
