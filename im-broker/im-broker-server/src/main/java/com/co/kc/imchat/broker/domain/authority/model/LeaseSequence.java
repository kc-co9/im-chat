package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * 同一 Broker 在权威租约序列中的单调序号值对象。
 *
 * <p>序号用于同一 Leader 任期内区分租约续发和旧租约重放；接收方只接受比当前租约更新
 * 的序号，并结合 quorum read index 判断证明是否来自足够新的权威状态。零值仅表示
 * 本机在本次 fence 前尚未见过租约。</p>
 */
public record LeaseSequence(Long value) implements Serializable, Comparable<LeaseSequence> {

    public static final LeaseSequence NONE = new LeaseSequence(0L);

    public LeaseSequence {
        AssertUtils.domainPropNotNull("lease sequence is required", value);
        AssertUtils.domainPropTrue("lease sequence must not be negative", value >= 0L);
    }

    public static LeaseSequence first() {
        return new LeaseSequence(1L);
    }

    public LeaseSequence next() {
        return new LeaseSequence(Math.addExact(value, 1L));
    }

    @Override
    public int compareTo(LeaseSequence other) {
        AssertUtils.domainPropNotNull("compared lease sequence is required", other);
        return Long.compare(value, other.value);
    }
}
