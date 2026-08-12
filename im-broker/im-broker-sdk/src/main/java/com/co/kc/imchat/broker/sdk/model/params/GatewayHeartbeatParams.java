package com.co.kc.imchat.broker.sdk.model.params;

/**
 * WS 网关实例心跳请求。
 *
 * @param gatewayId 网关实例 ID
 */
public record GatewayHeartbeatParams(String gatewayId) {
}
