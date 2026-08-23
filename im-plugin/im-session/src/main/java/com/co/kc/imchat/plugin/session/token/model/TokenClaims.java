package com.co.kc.imchat.plugin.session.token.model;

import java.util.Objects;

/**
 * 签发 Session Token 所需的业务 Claim。
 *
 * @param userId Token 所属用户 ID
 * @param sessionVersion Token 绑定的 Session 版本，用于单端登录和会话失效校验
 * @param tokenType Token 类型，用于区分 Access Token 与 Refresh Token
 */
public record TokenClaims(long userId, String sessionVersion, TokenType tokenType) {

    public TokenClaims {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (sessionVersion == null || sessionVersion.isBlank()) {
            throw new IllegalArgumentException("sessionVersion must not be blank");
        }
        Objects.requireNonNull(tokenType, "tokenType must not be null");
    }
}
