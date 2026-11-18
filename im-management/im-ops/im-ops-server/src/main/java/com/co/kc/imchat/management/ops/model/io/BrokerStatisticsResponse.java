package com.co.kc.imchat.management.ops.model.io;

/** Broker 注册表统计响应。 */
public record BrokerStatisticsResponse(Long brokerCount, Long gatewayCount, Long connectionCount) {
}
