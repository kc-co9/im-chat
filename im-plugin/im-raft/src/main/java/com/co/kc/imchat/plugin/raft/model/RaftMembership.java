package com.co.kc.imchat.plugin.raft.model;

import java.util.List;

/** 已提交的 Raft 投票成员和只复制日志的 learner 地址。 */
public record RaftMembership(List<String> voters, List<String> learners) {
    public RaftMembership {
        voters = List.copyOf(voters);
        learners = List.copyOf(learners);
    }
}
