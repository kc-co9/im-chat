package com.co.kc.imchat.broker.adapter.cluster.membership;

/**
 * Raft membership 变更结果，不向 Application 暴露 SOFAJRaft Status。
 */
public record BrokerConsensusMembershipResult(
        /* 当前成员配置步骤的处理状态，不表示 Broker 拓扑已提交。 */
        Status status,
        /* 当前已知的 Leader 成员通信地址；未知时为空。 */
        String leaderEndpoint,
        /* 成员配置拒绝、重试或不可用的原因。 */
        String message) {

    /**
     * 当前成员配置不允许执行请求，未发起本次配置变更。
     */
    public static BrokerConsensusMembershipResult rejected(
            String leaderEndpoint, String message) {
        return new BrokerConsensusMembershipResult(Status.REJECTED, leaderEndpoint, message);
    }

    /**
     * 当前配置已经满足该步骤，供幂等重试继续后续流程。
     */
    public static BrokerConsensusMembershipResult duplicate(
            String leaderEndpoint, String message) {
        return new BrokerConsensusMembershipResult(Status.DUPLICATE, leaderEndpoint, message);
    }

    public boolean accepted() {
        return status == Status.APPLIED || status == Status.DUPLICATE;
    }

    /**
     * 成员变更被明确拒绝，不包含无法确认提交结果或非 Leader 的情况。
     */
    public boolean rejected() {
        return status == Status.REJECTED;
    }

    public enum Status {
        /**
         * 成员配置已提交。
         */
        APPLIED,
        /**
         * 同一成员变更此前已经提交。
         */
        DUPLICATE,
        /**
         * 成员变更不满足当前配置约束。
         */
        REJECTED,
        /**
         * 当前节点不是 Raft leader。
         */
        NOT_LEADER,
        /**
         * 无法确认成员配置是否已提交。
         */
        UNAVAILABLE
    }
}
