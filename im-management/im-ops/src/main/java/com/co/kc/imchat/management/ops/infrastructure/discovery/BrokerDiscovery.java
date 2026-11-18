package com.co.kc.imchat.management.ops.infrastructure.discovery;

import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;

import java.util.List;

/** Broker 管理端点发现能力。 */
public interface BrokerDiscovery {
    List<BrokerManagementEndpoint> discover();

    /** 重新发现并解析唯一可信 Leader；无效、缺失或歧义提示在发送前拒绝。 */
    BrokerManagementEndpoint resolveLeader(String leaderHint);
}
