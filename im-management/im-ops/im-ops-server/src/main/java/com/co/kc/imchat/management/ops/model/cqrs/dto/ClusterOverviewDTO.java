package com.co.kc.imchat.management.ops.model.cqrs.dto;

import java.time.Instant;
import java.util.List;

/** Ops 汇总的集群总览。 */
public record ClusterOverviewDTO(
        Long brokerCount,
        Long gatewayCount,
        Long connectionCount,
        List<BrokerNodeOverviewDTO> nodes,
        Instant queriedAt) {
}
