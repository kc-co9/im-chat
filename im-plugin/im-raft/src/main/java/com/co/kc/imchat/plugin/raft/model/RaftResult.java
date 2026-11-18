package com.co.kc.imchat.plugin.raft.model;

/** 一次技术操作的完成结果；APPLIED 仅表示共识完成，业务是否接受由 value 表达。 */
public record RaftResult<R>(Status status, R value, String leaderEndpoint, String message) {
    public enum Status {
        APPLIED,
        NOT_LEADER,
        UNAVAILABLE
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }
}
