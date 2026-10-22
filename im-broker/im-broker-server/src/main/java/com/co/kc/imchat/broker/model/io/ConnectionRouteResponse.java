package com.co.kc.imchat.broker.model.io;


/** 单用户连接路由响应。 */
public record ConnectionRouteResponse(
        /* 用户标识。 */
        Long userId,
        /* Gateway 标识。 */
        String gatewayId,
        /* 注册时间。 */
        Long registeredAtMs,
        /* 最近一次接受的路由更新时间。 */
        Long lastSeenAtMs
) {
}
