package com.co.kc.imchat.application.model.cqrs.command.chat;

public record ImChatExitCmd(
        /* 用户ID */
        Long userId
) {
}
