package com.co.kc.imchat.management.ops.infrastructure.client.model;
/** Gateway 节点协议载荷。 */
public record GatewayNodePayload(String gatewayId, String host, Integer port,
                                 Long registeredAtMs, Long lastSeenAtMs) { }
