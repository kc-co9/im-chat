package com.co.kc.imchat.broker.adapter.cluster.membership;

import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex;

/** 成员排空专用读取事实，保留本机角色 generation 到 worker 消费与提交边界。 */
public record BrokerMembershipPermitDrain(
        /* 读取事实对应的 Leader 任期。 */
        LeaderTerm leaderTerm,
        /* 本机角色事件 generation。 */
        long generation,
        /* 已取得的法定人数读取索引。 */
        QuorumReadIndex readIndex) {
}
