package com.co.kc.imchat.management.ops.model.cqrs.dto;

import java.util.List;

/** Ops 应用层可见的权威 Broker 集群状态。 */
public record BrokerClusterStateDTO(
        long revision,
        long epoch,
        String algorithm,
        List<BrokerClusterNodeDTO> brokers) {
}
