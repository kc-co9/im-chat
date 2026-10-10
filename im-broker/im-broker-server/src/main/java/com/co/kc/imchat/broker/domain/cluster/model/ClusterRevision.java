package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * Broker 集群权威状态修订号值对象。
 *
 * <p>revision 覆盖集群拓扑、成员角色和流量状态等已提交内容；每次成功提交的领域状态
 * 变化都会递增。管理操作把它作为乐观并发条件，防止基于旧快照的提议覆盖较新的集群状态。</p>
 */
public record ClusterRevision(
        /* 任意已提交集群内容变化后递增的修订号。 */
        Long value) implements Serializable {
    public ClusterRevision {
        AssertUtils.domainPropNotNull("cluster revision is required", value);
        AssertUtils.domainPropTrue("cluster revision must not be negative", value >= 0L);
    }

    public ClusterRevision next() {
        return new ClusterRevision(Math.addExact(value, 1L));
    }
}
