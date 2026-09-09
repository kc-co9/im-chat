package com.co.kc.imchat.management.monitor.infrastructure.client.model;
import java.time.Instant;
/** Broker 节点协议载荷。 */
public record BrokerNodePayload(String brokerId, String host, Integer port,
                                Instant registeredAt, Instant lastSeenAt) { }
