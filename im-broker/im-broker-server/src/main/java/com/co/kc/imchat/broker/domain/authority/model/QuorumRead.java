package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 当前 Leader 任期内的多数派读取证据值对象。
 *
 * <p>它把 Leader 任期与已确认日志位置绑定，作为签发租约或创建 fence 的权威前置条件。
 * 单独的时间戳或单个节点读取不能替代该证据，因为它们无法证明状态已经被当前多数派
 * 接受。</p>
 */
public record QuorumRead(
        /* 完成多数派确认时的 Raft Leader term。 */
        LeaderTerm leaderTerm,
        /* 多数派已确认的日志读取位置。 */
        QuorumReadIndex readIndex) {

    public QuorumRead {
        AssertUtils.allDomainPropNotNull("quorum read evidence is invalid",
                leaderTerm, readIndex);
    }
}
