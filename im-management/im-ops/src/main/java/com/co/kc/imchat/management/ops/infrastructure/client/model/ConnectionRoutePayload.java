package com.co.kc.imchat.management.ops.infrastructure.client.model;
/** 连接路由协议载荷。 */
public record ConnectionRoutePayload(Long userId, String gatewayId,
                                     Long registeredAtMs, Long lastSeenAtMs) { }
