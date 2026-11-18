package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * Broker 集群权威状态修订号值对象。
 *
 * <p>revision 用于集群变更的乐观并发控制。操作登记、拓扑提交及排空完成等明确变更
 * 递增 revision；operation 阶段、恢复证据和 fence 更新不单独递增。</p>
 */
public record ClusterRevision(
        /* 集群变更并发控制使用的单调修订号。 */
        Long value) implements Serializable {
    public ClusterRevision {
        AssertUtils.domainPropNotNull("cluster revision is required", value);
        AssertUtils.domainPropTrue("cluster revision must not be negative", value >= 0L);
    }

    public ClusterRevision next() {
        return new ClusterRevision(Math.addExact(value, 1L));
    }
}
