package com.co.kc.imchat.service.account.facade.params;

import java.io.Serializable;

/**
 * Access Token 认证参数。
 *
 * @param token 待认证的 Access Token
 */
public record AccessTokenParams(String token) implements Serializable {
    public AccessTokenParams {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
    }
}
