package com.co.kc.imchat.gateway.ws.security.identity;

/**
 * WS 连接认证后的用户身份。
 *
 * @param userId 当前连接归属的用户 ID
 * @param sessionVersion 账号服务认证的会话版本
 */
public record WsPrincipal(Long userId, String sessionVersion) {

    public WsPrincipal {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (sessionVersion == null || sessionVersion.isBlank()) {
            throw new IllegalArgumentException("sessionVersion must not be blank");
        }
    }
}
