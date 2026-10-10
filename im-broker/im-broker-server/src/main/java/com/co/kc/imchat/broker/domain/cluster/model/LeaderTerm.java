package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * 签发路由权威证明时的 Raft Leader 任期值对象。
 *
 * <p>租约和 fence 必须携带签发时的任期，接收方通过比较任期拒绝旧 Leader 产生的证明。
 * 它只表达共识任期，不表示集群业务 revision 或路由 epoch。</p>
 */
public record LeaderTerm(Long value) implements Serializable, Comparable<LeaderTerm> {

    public LeaderTerm {
        AssertUtils.domainPropNotNull("leader term is required", value);
        AssertUtils.domainPropTrue("leader term must be positive", value >= 1L);
    }

    @Override
    public int compareTo(LeaderTerm other) {
        AssertUtils.domainPropNotNull("compared leader term is required", other);
        return Long.compare(value, other.value);
    }
}
