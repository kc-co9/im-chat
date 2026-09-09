package com.co.kc.imchat.management.monitor.model.io;

/** Broker 当前实例响应。 */
public record BrokerInstanceResponse(String id, String address, Long startedAt, Long uptimeSeconds) {
}
