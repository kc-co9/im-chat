package com.co.kc.imchat.broker.sdk.model.params;

import java.io.Serializable;

/**
 * 用户网关路由注册请求。
 *
 * @param userId    连接归属用户 ID
 * @param gatewayId 用户所在 WS 网关实例 ID
 */
public record ConnectionRegisterParams(Long userId, String gatewayId) implements Serializable {
}
