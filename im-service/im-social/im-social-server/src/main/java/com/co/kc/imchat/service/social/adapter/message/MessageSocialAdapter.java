package com.co.kc.imchat.service.social.adapter.message;

import com.co.kc.imchat.common.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.service.message.facade.ChatService;
import com.co.kc.imchat.service.message.facade.dto.UserGroupChatSummaryDTO;
import com.co.kc.imchat.service.message.facade.params.PrivateChatPrepareParams;
import com.co.kc.imchat.service.message.facade.params.PrivateChatRemoveParams;
import com.co.kc.imchat.service.message.facade.params.UserGroupChatSummaryGetParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsCreateParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsDismissParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsJoinParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatMemberRemoveParams;
import com.co.kc.imchat.service.message.facade.params.UserGroupChatSummariesGetParams;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.service.social.domain.message.model.UserGroupChatSummary;
import com.co.kc.imchat.service.social.transformer.MessageSocialAdapterTransformer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 社交服务访问消息侧聊天同步能力的适配器。
 */
public class MessageSocialAdapter {

    private ChatService chatService;

    public MessageSocialAdapter() {
    }

    public MessageSocialAdapter(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 通知消息侧好友已新增。
     *
     * @param userId       用户 ID
     * @param friendUserId 好友用户 ID
     */
    public void onFriendAdded(Long userId, Long friendUserId) {
        chatService.preparePrivateChat(new PrivateChatPrepareParams(userId, friendUserId));
    }

    /**
     * 通知消息侧好友已删除。
     *
     * @param userId       用户 ID
     * @param friendUserId 好友用户 ID
     */
    public void onFriendRemoved(Long userId, Long friendUserId) {
        chatService.removePrivateChat(new PrivateChatRemoveParams(userId, friendUserId));
    }

    /**
     * 通知消息侧群已创建。
     *
     * @param groupId 群 ID
     * @param ownerId 群主用户 ID
     * @param members 初始成员
     */
    public void onGroupCreated(Long groupId, Long ownerId, List<GroupMember> members) {
        chatService.createGroupChats(new GroupChatsCreateParams(
                groupId,
                ownerId,
                MessageSocialAdapterTransformer.INSTANCE.groupChatMemberParamsListFrom(members)));
    }

    /**
     * 通知消息侧群已解散。
     *
     * @param groupId 群 ID
     * @param ownerId 群主用户 ID
     */
    public void onGroupDismissed(Long groupId, Long ownerId) {
        chatService.dismissGroupChats(new GroupChatsDismissParams(groupId, ownerId));
    }

    /**
     * 通知消息侧群成员已加入。
     *
     * @param inviterId         邀请人用户 ID
     * @param groupId           群 ID
     * @param members           新成员
     * @param memberDescriptors 新成员展示信息
     */
    public void onGroupMemberJoined(Long inviterId,
                                    Long groupId,
                                    List<GroupMember> members,
                                    List<MemberDescriptor> memberDescriptors) {
        chatService.joinGroupChats(new GroupChatsJoinParams(
                inviterId,
                groupId,
                MessageSocialAdapterTransformer.INSTANCE.groupChatMemberParamsListFrom(members),
                MessageSocialAdapterTransformer.INSTANCE.groupChatMemberDescriptorParamsListFrom(memberDescriptors)));
    }

    /**
     * 通知消息侧群成员已移除。
     *
     * @param groupId 群 ID
     * @param userId  被移除用户 ID
     */
    public void onGroupMemberRemoved(Long groupId, Long userId) {
        chatService.removeGroupChatMember(new GroupChatMemberRemoveParams(groupId, userId));
    }

    /**
     * 查询用户单个群聊会话摘要。
     *
     * @param userId  用户 ID
     * @param groupId 群 ID
     * @return 群聊会话摘要
     */
    public Optional<UserGroupChatSummary> getUserGroupChatSummary(Long userId, Long groupId) {
        UserGroupChatSummaryDTO response =
                chatService.getUserGroupChatSummary(new UserGroupChatSummaryGetParams(userId, groupId));
        if (!response.found()) {
            return Optional.empty();
        }
        return Optional.of(MessageSocialAdapterTransformer.INSTANCE.userGroupChatSummaryFrom(response));
    }

    /**
     * 查询用户多个群聊会话摘要。
     *
     * @param userId   用户 ID
     * @param groupIds 群 ID 集合
     * @return 群聊会话摘要列表
     */
    public List<UserGroupChatSummary> getUserGroupChatSummaries(Long userId, Set<Long> groupIds) {
        return chatService.getUserGroupChatSummaries(new UserGroupChatSummariesGetParams(userId, groupIds))
                .stream()
                .filter(UserGroupChatSummaryDTO::found)
                .map(MessageSocialAdapterTransformer.INSTANCE::userGroupChatSummaryFrom)
                .toList();
    }
}
