package com.co.kc.imchat.broker.support.diagnostic.model.io;

import java.time.Instant;

/** 单用户连接路由响应。 */
public record ConnectionRouteResponse(
        /* 用户标识。 */
        Long userId,
        /* Gateway 标识。 */
        String gatewayId,
        /* 注册时间。 */
        Instant registeredAt,
        /* 最后心跳时间。 */
        Instant lastSeenAt
) {
}
