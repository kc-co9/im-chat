package com.co.kc.imchat.management.ops.infrastructure.client.model;

import java.util.List;

/** Broker 权威 cluster-state 载荷。 */
public record BrokerClusterStatePayload(
        long revision,
        long epoch,
        String algorithm,
        List<BrokerClusterNodePayload> brokers) {
}
