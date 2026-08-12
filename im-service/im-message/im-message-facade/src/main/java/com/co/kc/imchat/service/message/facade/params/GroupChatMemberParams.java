package com.co.kc.imchat.service.message.facade.params;

import java.time.LocalDateTime;

/**
 * 消息侧群成员信息。
 *
 * @param groupId  群 ID
 * @param userId   成员用户 ID
 * @param joinTime 成员入群时间
 */
public record GroupChatMemberParams(Long groupId, Long userId, LocalDateTime joinTime) {
}
