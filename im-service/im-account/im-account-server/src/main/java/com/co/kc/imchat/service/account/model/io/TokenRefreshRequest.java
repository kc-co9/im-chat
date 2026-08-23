package com.co.kc.imchat.service.account.model.io;

import jakarta.validation.constraints.NotBlank;

/** 使用 Refresh Token 获取新凭证对的请求。 */
public record TokenRefreshRequest(
        /** 当前有效的 Refresh Token。 */
        @NotBlank(message = "Refresh Token不能为空") String refreshToken
) {
}
