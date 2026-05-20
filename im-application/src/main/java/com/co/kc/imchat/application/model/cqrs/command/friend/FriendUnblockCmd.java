package com.co.kc.imchat.application.model.cqrs.command.friend;

public record FriendUnblockCmd(
        /* 用户ID */
        Long userId,
        /* 好友用户ID */
        Long friendUserId
) {
}
