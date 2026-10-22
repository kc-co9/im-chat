package com.co.kc.imchat.management.ops.infrastructure.client.model;
/** Broker 节点协议载荷。 */
public record BrokerNodePayload(String brokerId, String host, Integer port,
                                Long registeredAtMs, Long lastSeenAtMs) { }
