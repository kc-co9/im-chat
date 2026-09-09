package com.co.kc.imchat.management.monitor.model.cqrs.dto;

import java.time.Instant;

/** Gateway 注册表节点快照。 */
public record GatewayNodeDTO(
        String gatewayId,
        String host,
        Integer port,
        Instant registeredAt,
        Instant lastSeenAt
) {
}
