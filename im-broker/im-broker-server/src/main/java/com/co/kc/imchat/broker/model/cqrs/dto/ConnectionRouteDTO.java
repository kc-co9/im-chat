package com.co.kc.imchat.broker.model.cqrs.dto;

import java.time.Instant;

/**
 * 单个用户到 Gateway 的连接路由快照。
 */
public record ConnectionRouteDTO(
        /* 用户标识。 */
        Long userId,
        /* Gateway 标识。 */
        String gatewayId,
        /* 注册时间。 */
        Instant registeredAt,
        /* 最近一次接受的路由更新时间。 */
        Instant lastSeenAt
) {
}
