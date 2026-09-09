package com.co.kc.imchat.management.monitor.model.cqrs.dto;

import java.time.Instant;

/** 单用户连接路由快照。 */
public record ConnectionRouteDTO(Long userId, String gatewayId, Instant registeredAt, Instant lastSeenAt) {
}
