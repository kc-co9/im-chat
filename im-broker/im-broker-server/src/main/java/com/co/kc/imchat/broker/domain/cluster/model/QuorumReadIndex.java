package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * 多数派确认后的线性一致读取位置值对象。
 *
 * <p>该位置表示控制面已经从当前 Leader 任期的多数派读取到的日志水位。它和
 * {@link LeaderTerm} 一起组成租约、fence 等权威证明：读取位置不能为零或负数，较大的
 * 位置表示在同一任期内观察到了更多已确认状态。</p>
 */
public record QuorumReadIndex(Long value)
        implements Serializable, Comparable<QuorumReadIndex> {

    public QuorumReadIndex {
        AssertUtils.domainPropNotNull("quorum read index is required", value);
        AssertUtils.domainPropTrue("quorum read index must be positive", value >= 1L);
    }

    @Override
    public int compareTo(QuorumReadIndex other) {
        AssertUtils.domainPropNotNull("compared quorum read index is required", other);
        return Long.compare(value, other.value);
    }
}
