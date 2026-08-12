package com.co.kc.imchat.service.message.facade.dto;

import java.time.LocalDateTime;

/**
 * 用户群聊会话摘要。
 *
 * @param found              是否存在会话
 * @param chatId             会话 ID
 * @param groupId            群 ID
 * @param userId             用户 ID
 * @param unreadMessageCount 未读消息数
 * @param activeTime         最近活跃时间
 */
public record UserGroupChatSummaryDTO(
        boolean found,
        Long chatId,
        Long groupId,
        Long userId,
        Integer unreadMessageCount,
        LocalDateTime activeTime) {
    public static UserGroupChatSummaryDTO empty() {
        return new UserGroupChatSummaryDTO(false, null, null, null, null, null);
    }
}
