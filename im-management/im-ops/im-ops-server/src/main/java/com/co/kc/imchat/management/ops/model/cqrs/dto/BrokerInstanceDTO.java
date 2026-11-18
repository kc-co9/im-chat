package com.co.kc.imchat.management.ops.model.cqrs.dto;

import java.time.Instant;
import java.time.Duration;

/** Broker 当前实例的节点级运行信息。 */
public record BrokerInstanceDTO(String id, String address, Instant startedAt, Duration uptime) {
}
