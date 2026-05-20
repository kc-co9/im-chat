package com.co.kc.imchat.application.model.cqrs.command.chat;

public record ImPrivateChatOpenCmd(
        /* 用户ID */
        Long userId,
        /* 对端用户ID */
        Long peerUserId
) {
}
