package com.co.kc.imchat.management.monitor.model.cqrs.dto;

import java.time.Instant;

/** Broker 注册表节点快照。 */
public record BrokerNodeDTO(
        String brokerId,
        String host,
        Integer port,
        Instant registeredAt,
        Instant lastSeenAt
) {
}
