package com.co.kc.imchat.management.monitor.model.cqrs.dto;

/** Broker 注册表统计快照。 */
public record BrokerStatisticsDTO(Long brokerCount, Long gatewayCount, Long connectionCount) {
}
