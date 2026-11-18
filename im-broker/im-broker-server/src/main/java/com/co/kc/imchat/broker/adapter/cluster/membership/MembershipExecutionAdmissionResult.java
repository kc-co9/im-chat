package com.co.kc.imchat.broker.adapter.cluster.membership;

/**
 * 本机阶段投递结果；ACCEPTED仅表示入队，不表示共识应用或成员操作成功。
 */
public record MembershipExecutionAdmissionResult(
        /* 本机准入状态。 */ Status status,
        /* 仅用于拒绝后的寻址提示。 */ String leaderEndpoint,
        /* 准入原因。 */ String message) {
    public boolean accepted() {
        return status == Status.ACCEPTED;
    }

    public enum Status { ACCEPTED, NOT_LEADER, UNAVAILABLE }
}
