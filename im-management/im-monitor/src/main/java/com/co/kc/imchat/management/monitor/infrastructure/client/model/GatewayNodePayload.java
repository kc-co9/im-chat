package com.co.kc.imchat.management.monitor.infrastructure.client.model;
import java.time.Instant;
/** Gateway 节点协议载荷。 */
public record GatewayNodePayload(String gatewayId, String host, Integer port,
                                 Instant registeredAt, Instant lastSeenAt) { }
