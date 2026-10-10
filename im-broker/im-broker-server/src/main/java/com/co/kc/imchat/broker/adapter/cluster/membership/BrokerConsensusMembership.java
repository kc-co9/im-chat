package com.co.kc.imchat.broker.adapter.cluster.membership;

import java.util.List;

/**
 * 当前 Raft 配置的 transport-neutral 视图。
 */
public record BrokerConsensusMembership(List<String> voters, List<String> learners) {

    public BrokerConsensusMembership {
        if (voters == null || learners == null) {
            throw new IllegalArgumentException("membership lists must not be null");
        }
        voters = voters.stream().sorted().toList();
        learners = learners.stream().sorted().toList();
    }
}
