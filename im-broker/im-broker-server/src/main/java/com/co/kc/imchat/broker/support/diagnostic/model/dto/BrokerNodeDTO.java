package com.co.kc.imchat.broker.support.diagnostic.model.dto;

import java.time.Instant;

/**
 * Broker 注册实例只读快照。
 */
public record BrokerNodeDTO(
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
