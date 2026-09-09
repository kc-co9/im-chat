package com.co.kc.imchat.broker.support.diagnostic.model.io;

import java.time.Instant;

/** Broker 注册快照响应。 */
public record BrokerNodeResponse(
        /* Broker 标识。 */
        String brokerId,
        /* Broker 主机。 */
        String host,
        /* Broker Bolt 端口。 */
        Integer port,
        /* 注册时间。 */
        Instant registeredAt,
        /* 最后心跳时间。 */
        Instant lastSeenAt
) {
}
