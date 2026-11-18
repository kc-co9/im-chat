package com.co.kc.imchat.management.ops.model.cqrs.dto;

/** Broker 注册表统计快照。 */
public record BrokerStatisticsDTO(Long brokerCount, Long gatewayCount, Long connectionCount) {
}
