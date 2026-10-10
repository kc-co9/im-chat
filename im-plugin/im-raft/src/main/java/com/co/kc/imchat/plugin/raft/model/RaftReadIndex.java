package com.co.kc.imchat.plugin.raft.model;

/** 在同一当前 Leader 任期内完成的多数派读取证据。 */
public record RaftReadIndex(long term, long index) {
}
