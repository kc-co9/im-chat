package com.co.kc.imchat.management.monitor.model.io;

/** Broker 注册表节点响应。 */
public record BrokerNodeResponse(String brokerId, String host, Integer port,
                                 Long registeredAt, Long lastSeenAt) {
}
