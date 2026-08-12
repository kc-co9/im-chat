package com.co.kc.imchat.service.message.facade.params;

/**
 * 群聊成员会话移除请求。
 *
 * @param groupId 群 ID
 * @param userId 被移除的成员用户 ID
 */
public record GroupChatMemberRemoveParams(Long groupId, Long userId) {
}
