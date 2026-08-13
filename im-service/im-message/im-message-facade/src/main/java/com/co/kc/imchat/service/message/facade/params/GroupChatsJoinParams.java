package com.co.kc.imchat.service.message.facade.params;

import java.io.Serializable;
import java.util.List;

/**
 * 群聊会话新成员同步请求。
 *
 * @param inviterId 邀请人用户 ID
 * @param groupId 群 ID
 * @param members 新加入的成员列表
 * @param memberDescriptors 新成员展示描述
 */
public record GroupChatsJoinParams(
        Long inviterId,
        Long groupId,
        List<GroupChatMemberParams> members,
        List<GroupChatMemberDescriptorParams> memberDescriptors) implements Serializable {
}
