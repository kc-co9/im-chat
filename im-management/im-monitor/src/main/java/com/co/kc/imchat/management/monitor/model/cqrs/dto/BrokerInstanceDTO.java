package com.co.kc.imchat.management.monitor.model.cqrs.dto;

import java.time.Instant;

/** Broker 当前实例的节点级运行信息。 */
public record BrokerInstanceDTO(String id, String address, Instant startedAt, Long uptimeSeconds) {
}
