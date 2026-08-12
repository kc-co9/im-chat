package com.co.kc.imchat.service.message.facade.params;

/**
 * 群聊会话解散请求。
 *
 * @param groupId 群 ID
 * @param ownerId 解散操作人 ID
 */
public record GroupChatsDismissParams(Long groupId, Long ownerId) {
}
