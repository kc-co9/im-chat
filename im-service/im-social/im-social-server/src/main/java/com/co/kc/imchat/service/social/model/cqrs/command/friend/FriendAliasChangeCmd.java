package com.co.kc.imchat.service.social.model.cqrs.command.friend;

public record FriendAliasChangeCmd(
        /* 用户ID */
        Long userId,
        /* 好友用户ID */
        Long friendUserId,
        /* 好友备注 */
        String friendAlias
) {
}
