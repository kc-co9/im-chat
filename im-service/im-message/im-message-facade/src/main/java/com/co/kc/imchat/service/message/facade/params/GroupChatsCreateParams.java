package com.co.kc.imchat.service.message.facade.params;

import java.io.Serializable;
import java.util.List;

/**
 * 群聊会话创建请求。
 *
 * @param groupId 群 ID
 * @param ownerId 群主用户 ID
 * @param members 创建群时的成员列表
 */
public record GroupChatsCreateParams(Long groupId, Long ownerId, List<GroupChatMemberParams> members) implements Serializable {
}
