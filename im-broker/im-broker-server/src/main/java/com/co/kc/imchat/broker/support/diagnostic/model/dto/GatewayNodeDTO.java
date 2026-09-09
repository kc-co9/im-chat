package com.co.kc.imchat.broker.support.diagnostic.model.dto;

import java.time.Instant;

/**
 * Gateway 注册实例只读快照。
 */
public record GatewayNodeDTO(
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
