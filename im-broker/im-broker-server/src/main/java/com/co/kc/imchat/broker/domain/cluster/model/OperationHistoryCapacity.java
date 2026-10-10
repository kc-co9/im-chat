package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * BrokerCluster 聚合保留 operation 历史的容量上限值对象。
 *
 * <p>容量用于限制未完成 operation 占用的聚合状态，避免新的拓扑操作在旧操作无法收口时
 * 无限累积。达到容量上限时，聚合返回不可用决定，而不是丢弃仍处于活动状态的操作。</p>
 */
public record OperationHistoryCapacity(Integer value) implements Serializable {

    public OperationHistoryCapacity {
        AssertUtils.domainPropNotNull("operation history capacity is required", value);
        AssertUtils.domainPropTrue("operation history capacity must be positive",
                value >= 1);
    }
}
