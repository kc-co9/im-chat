package com.co.kc.imchat.service.social.domain.message.model;

import java.time.LocalDateTime;

/**
 * 用户群聊会话摘要。
 *
 * @param chatId             会话 ID
 * @param groupId            群 ID
 * @param userId             用户 ID
 * @param unreadMessageCount 未读消息数
 * @param activeTime         最近活跃时间
 */
public record UserGroupChatSummary(
        Long chatId,
        Long groupId,
        Long userId,
        Integer unreadMessageCount,
        LocalDateTime activeTime) {
}
