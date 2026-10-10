package com.co.kc.imchat.management.ops.infrastructure.discovery;

import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;

import java.util.List;

/** Broker 管理端点发现能力。 */
public interface BrokerDiscovery {
    List<BrokerManagementEndpoint> discover();
}
