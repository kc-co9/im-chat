package com.co.kc.imchat.broker.support.diagnostic.model.dto;

import java.time.Instant;

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
        /* Broker 已运行秒数。 */
        Long uptimeSeconds
) {
}
