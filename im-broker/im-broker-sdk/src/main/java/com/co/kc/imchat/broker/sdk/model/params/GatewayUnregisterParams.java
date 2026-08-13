package com.co.kc.imchat.broker.sdk.model.params;

import java.io.Serializable;

/**
 * WS 网关实例注销请求。
 *
 * @param gatewayId 网关实例 ID
 */
public record GatewayUnregisterParams(String gatewayId) implements Serializable {
}
