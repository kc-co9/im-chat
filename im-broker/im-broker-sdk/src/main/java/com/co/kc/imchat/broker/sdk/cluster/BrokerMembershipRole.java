package com.co.kc.imchat.broker.sdk.cluster;

/**
 * Broker 在集群发现与 Raft membership 中的权威阶段。
 */
public enum BrokerMembershipRole {
    DISCOVERED,
    LEARNER_SYNCING,
    LEARNER,
    VOTER,
    REMOVED
}
