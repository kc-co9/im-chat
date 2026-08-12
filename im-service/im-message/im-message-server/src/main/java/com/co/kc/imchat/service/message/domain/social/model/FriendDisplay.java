package com.co.kc.imchat.service.message.domain.social.model;

/**
 * 好友展示信息。
 *
 * @param userId      好友用户 ID
 * @param displayName 好友展示名
 */
public record FriendDisplay(Long userId, String displayName) {
}
