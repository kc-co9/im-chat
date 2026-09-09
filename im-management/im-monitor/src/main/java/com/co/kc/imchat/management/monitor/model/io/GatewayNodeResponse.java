package com.co.kc.imchat.management.monitor.model.io;

/** Gateway 注册表节点响应。 */
public record GatewayNodeResponse(String gatewayId, String host, Integer port,
                                  Long registeredAt, Long lastSeenAt) {
}
