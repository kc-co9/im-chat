package com.co.kc.imchat.broker.model.cqrs.dto;

import java.time.Instant;
import java.time.Duration;

/**
 * 当前 Broker 实例运行快照。
 */
public record BrokerInstanceDTO(
        /* Broker 实例标识。 */
        String id,
        /* Broker Bolt 地址。 */
        String address,
        /* Broker 启动时间。 */
        Instant startedAt,
        /* Broker 已运行时长。 */
        Duration uptime
) {
}
