package com.co.kc.imchat.service.social.model.cqrs.query.friend;

public record FriendDetailQuery(
        /* 用户ID */
        Long userId,
        /* 好友用户ID */
        Long friendUserId
) {
}
