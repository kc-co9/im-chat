package com.co.kc.imchat.broker.sdk.model.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * WS 网关实例路由信息。
 *
 * @param gatewayId    网关实例 ID
 * @param host         网关内部通信地址
 * @param port         网关内部通信端口
 * @param registeredAt 网关首次注册时间
 * @param lastSeenAt   Broker 最后一次确认网关仍然活跃的时间
 */
public record GatewayEndpointDTO(String gatewayId, String host, int port, Instant registeredAt, Instant lastSeenAt) implements Serializable {
}
