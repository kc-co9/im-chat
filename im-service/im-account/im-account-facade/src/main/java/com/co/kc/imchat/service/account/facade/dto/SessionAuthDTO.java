package com.co.kc.imchat.service.account.facade.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Access Token 会话认证成功结果，不携带账号 Session 持久化信息。
 *
 * @param userId               已认证用户 ID
 * @param sessionVersion       已认证会话版本
 * @param accessTokenExpiresAt Access Token 过期时间
 */
public record SessionAuthDTO(
        Long userId,
        String sessionVersion,
        Instant accessTokenExpiresAt
) implements Serializable {
    public SessionAuthDTO {
        Objects.requireNonNull(userId, "userId must not be null");
        if (sessionVersion == null || sessionVersion.isBlank()) {
            throw new IllegalArgumentException("sessionVersion must not be blank");
        }
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt must not be null");
    }
}
