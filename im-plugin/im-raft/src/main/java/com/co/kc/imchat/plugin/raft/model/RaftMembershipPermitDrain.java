package com.co.kc.imchat.plugin.raft.model;

/** 专用于成员许可排空 CAS 的读取事实；消费者必须复验同一 term/generation。 */
public record RaftMembershipPermitDrain(
        /* JRaft 读取事实对应的任期。 */
        long term,
        /* 角色事件 generation。 */
        long generation,
        /* 法定人数读取索引。 */
        long index) {
}
