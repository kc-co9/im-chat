package com.co.kc.imchat.service.social.domain.friend.model;

import com.co.kc.imchat.common.domain.user.model.UserId;

import java.time.Instant;

/**
 * 当前用户视角下的好友资料。
 *
 * @param friendUserId 好友用户 ID
 * @param displayName 好友展示名称
 * @param status      好友关系状态
 * @param createTime  好友关系创建时间
 */
public record FriendProfile(
        UserId friendUserId,
        FriendDisplayName displayName,
        FriendStatus status,
        Instant createTime
) {
}
