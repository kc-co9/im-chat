package com.co.kc.imchat.plugin.raft.model;

/** 单次原生配置请求的结果，不证明全局稳定或维护许可排空。 */
public record RaftConfigurationResult(
        /* 原生配置调用状态。 */
        Status status,
        /* 原生错误码。 */
        int errorCode,
        /* 非 Leader 时返回的 Leader 端点。 */
        String leaderEndpoint,
        /* 面向调用方的结果说明。 */
        String message) {
    /** 原生配置入口的有限结果集合。 */
    public enum Status {
        APPLIED, BUSY, NOT_LEADER, UNAVAILABLE
    }
}
