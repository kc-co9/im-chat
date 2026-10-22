package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * BrokerCluster 聚合保留 operation 历史的容量上限值对象。
 *
 * <p>容量限制全部 operation 历史记录。满额时优先回收最早的终态记录；全部记录均
 * 未终结时返回不可用，不丢弃活动操作。</p>
 */
public record OperationHistoryCapacity(Integer value) implements Serializable {

    public OperationHistoryCapacity {
        AssertUtils.domainPropNotNull("operation history capacity is required", value);
        AssertUtils.domainPropTrue("operation history capacity must be positive",
                value >= 1);
    }
}
