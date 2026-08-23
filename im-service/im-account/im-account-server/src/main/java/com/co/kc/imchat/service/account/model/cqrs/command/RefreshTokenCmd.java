package com.co.kc.imchat.service.account.model.cqrs.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新会话凭证命令。
 *
 * @param refreshToken Refresh Token
 */
public record RefreshTokenCmd(
        @NotBlank(message = "Refresh Token不能为空") String refreshToken
) {
}
