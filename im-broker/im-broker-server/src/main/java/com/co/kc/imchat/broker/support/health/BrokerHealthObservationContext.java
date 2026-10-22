package com.co.kc.imchat.broker.support.health;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterTopology;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.common.utils.AssertUtils;

/** 本机健康采样的结构化版本和拓扑，用于隔离拓扑变化后的迟到结果。 */
public record BrokerHealthObservationContext(
        /* 采样绑定的已提交版本。 */
        ClusterRevision revision,
        /* 采样绑定的业务拓扑与实际成员配置。 */
        BrokerClusterTopology topology) {
    public BrokerHealthObservationContext {
        AssertUtils.argNotNull("revision is required", revision);
        AssertUtils.argNotNull("topology is required", topology);
    }
}
