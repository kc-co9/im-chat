package com.co.kc.imchat.service.account.model.io;

import java.time.Instant;

/** 登录或刷新成功后返回的访问凭证对。 */
public record TokenPairResponse(
        /** 用户标识。 */
        Long userId,
        /** 用于访问受保护资源的 Access Token。 */
        String accessToken,
        /** Access Token 的过期时间。 */
        Instant accessTokenExpiresAt,
        /** 用于刷新 Access Token 的 Refresh Token。 */
        String refreshToken,
        /** Refresh Token 的过期时间。 */
        Instant refreshTokenExpiresAt
) {
}
