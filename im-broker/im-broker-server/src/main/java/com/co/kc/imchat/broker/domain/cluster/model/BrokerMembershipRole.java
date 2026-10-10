package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * Broker 在集群共识成员关系中的领域角色。
 *
 * <p>该角色描述节点是否存在于 Raft 成员配置、是否参与日志复制以及是否拥有投票权；
 * 它与 {@link BrokerTrafficState} 分离，避免把“能否投票”和“能否承载用户路由”混为一谈。</p>
 */
public enum BrokerMembershipRole {
    /** 被发现但未加入 Raft 成员配置。 */
    DISCOVERED,
    /** learner 正在追赶已提交日志。 */
    LEARNER_SYNCING,
    /** 复制日志但不参与投票。 */
    LEARNER,
    /** 参与 Raft 多数派投票。 */
    VOTER
}
