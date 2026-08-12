package com.co.kc.imchat.gateway.ws.security.identity;

/**
 * WS 连接认证后的用户身份。
 *
 * @param userId 当前连接归属的用户 ID
 */
public record WsPrincipal(Long userId) {

    public WsPrincipal {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
    }
}
