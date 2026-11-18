package com.co.kc.imchat.broker.adapter.cluster.membership;

/**
 * Broker自有原生成员执行观察；完成观察不自动成为许可排空或共识授权。
 */
public record MembershipConfigurationResult(
        /* 原生配置观察状态。 */ Status status,
        /* 原始错误码。 */ int nativeCode,
        /* 原始Leader寻址提示。 */ String leaderEndpoint,
        /* 原始具体原因。 */ String message) {
    public enum Status { APPLIED, BUSY, NOT_LEADER, UNAVAILABLE }
}
