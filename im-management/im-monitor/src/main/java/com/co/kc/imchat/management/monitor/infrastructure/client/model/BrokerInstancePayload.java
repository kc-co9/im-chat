package com.co.kc.imchat.management.monitor.infrastructure.client.model;
import java.time.Instant;
/** Broker 实例协议载荷。 */
public record BrokerInstancePayload(String id, String address, Instant startedAt, Long uptimeSeconds) { }
