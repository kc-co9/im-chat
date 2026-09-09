package com.co.kc.imchat.broker.support.diagnostic.model.io;

import java.time.Instant;

/** Gateway 注册快照响应。 */
public record GatewayNodeResponse(
        /* Gateway 标识。 */
        String gatewayId,
        /* Gateway 主机。 */
        String host,
        /* Gateway 端口。 */
        Integer port,
        /* 注册时间。 */
        Instant registeredAt,
        /* 最后心跳时间。 */
        Instant lastSeenAt
) {
}
