package com.co.kc.imchat.service.message.domain.chat.model;

import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.user.model.UserId;

/**
 * 消息服务创建群聊会话所需的群成员快照。
 *
 * @param groupId 群 ID
 * @param userId  成员用户 ID
 */
public record GroupChatMember(GroupId groupId, UserId userId) {
}
