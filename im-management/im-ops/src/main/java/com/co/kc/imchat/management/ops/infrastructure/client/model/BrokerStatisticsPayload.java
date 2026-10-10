package com.co.kc.imchat.management.ops.infrastructure.client.model;
/** Broker 统计协议载荷。 */
public record BrokerStatisticsPayload(Long brokerCount, Long gatewayCount, Long connectionCount) { }
