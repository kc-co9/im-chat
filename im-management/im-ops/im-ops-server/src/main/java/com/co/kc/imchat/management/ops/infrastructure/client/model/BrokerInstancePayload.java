package com.co.kc.imchat.management.ops.infrastructure.client.model;
/** Broker 实例协议载荷。 */
public record BrokerInstancePayload(String id, String address, Long startedAtMs, Long uptimeMs) { }
