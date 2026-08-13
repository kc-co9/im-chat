package com.co.kc.imchat.service.social.facade.dto;

import java.io.Serializable;

/**
 * 好友展示信息。
 *
 * @param userId      好友用户 ID
 * @param displayName 好友展示名
 */
public record FriendDisplayDTO(Long userId, String displayName) implements Serializable {
}
