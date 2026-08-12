package com.co.kc.imchat.broker.sdk.model.dto;

import java.time.Instant;

/**
 * 用户网关路由信息。
 *
 * @param userId      连接归属用户 ID
 * @param gatewayId   用户所在 WS 网关实例 ID
 * @param connectedAt 路由首次注册时间
 * @param refreshedAt Broker 最后一次刷新路由记录的时间
 */
public record UserGatewayDTO(
        Long userId,
        String gatewayId,
        Instant connectedAt,
        Instant refreshedAt
) {
}
