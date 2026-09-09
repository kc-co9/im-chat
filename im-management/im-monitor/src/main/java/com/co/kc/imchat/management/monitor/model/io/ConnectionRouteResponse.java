package com.co.kc.imchat.management.monitor.model.io;

/** 用户连接路由响应。 */
public record ConnectionRouteResponse(Long userId, String gatewayId,
                                      Long registeredAt, Long lastSeenAt) {
}
