package com.co.kc.imchat.service.message.facade.params;

/**
 * 查询用户群聊会话请求。
 *
 * @param userId  用户 ID
 * @param groupId 群 ID
 */
public record UserGroupChatSummaryGetParams(Long userId, Long groupId) {
}
