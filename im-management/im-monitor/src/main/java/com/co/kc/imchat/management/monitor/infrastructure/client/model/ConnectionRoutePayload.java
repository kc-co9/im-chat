package com.co.kc.imchat.management.monitor.infrastructure.client.model;
import java.time.Instant;
/** 连接路由协议载荷。 */
public record ConnectionRoutePayload(Long userId, String gatewayId,
                                     Instant registeredAt, Instant lastSeenAt) { }
