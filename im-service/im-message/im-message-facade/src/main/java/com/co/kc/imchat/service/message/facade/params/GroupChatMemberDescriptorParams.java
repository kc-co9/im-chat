package com.co.kc.imchat.service.message.facade.params;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群成员展示描述。
 *
 * @param userId 成员用户 ID
 * @param displayName 发送系统消息时展示的成员名称
 * @param joinTime 成员入群时间
 */
public record GroupChatMemberDescriptorParams(Long userId, String displayName, LocalDateTime joinTime) implements Serializable {
}
