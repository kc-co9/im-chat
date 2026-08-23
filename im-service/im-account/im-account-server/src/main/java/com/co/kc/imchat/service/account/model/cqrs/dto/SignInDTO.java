package com.co.kc.imchat.service.account.model.cqrs.dto;

import java.time.Instant;

/**
 * 用户认证响应DTO
 *
 * @author kc
 */
public record SignInDTO(
        Long userId,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
}
