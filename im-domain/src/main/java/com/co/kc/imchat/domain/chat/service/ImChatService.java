package com.co.kc.imchat.domain.chat.service;

import com.co.kc.imchat.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.domain.chat.model.GroupChatJoin;
import com.co.kc.imchat.domain.chat.model.GroupChatMembership;
import com.co.kc.imchat.domain.chat.model.ImChat;
import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.chat.model.ImChatName;
import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.domain.chat.model.ImUserChatDescriptor;
import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupAlias;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.repository.GroupRepository;
import com.co.kc.imchat.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.model.ImMessage;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.session.model.Session;
import com.co.kc.imchat.domain.session.repository.SessionRepository;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IM聊天-领域服务
 */
@RequiredArgsConstructor
public class ImChatService {
    private final FriendRepository friendRepository;
    private final ImPrivateChatRepository imPrivateChatRepository;
    private final GroupRepository groupRepository;
    private final ImGroupChatRepository imGroupChatRepository;
    private final SessionRepository sessionRepository;
    private final SnowflakeId snowflakeId;

    public Optional<ImPrivateChat> getPeerChat(ImPrivateChat imChat) {
        if (imChat == null) {
            throw new IllegalArgumentException("聊天会话传入为空");
        }
        return imPrivateChatRepository.find(imChat.getPeerUserId(), imChat.getUserId());
    }

    public List<ImUserChatDescriptor> getUserChatList(UserId userId) {
        List<ImPrivateChat> imPrivateChatList = imPrivateChatRepository.find(userId);
        List<ImUserChatDescriptor> imPrivateChatDescriptors = this.buildPrivateChatDescriptors(userId, imPrivateChatList);

        List<ImGroupChat> imGroupChatList = imGroupChatRepository.find(userId);
        List<ImUserChatDescriptor> imGroupChatDescriptors = this.buildGroupChatDescriptors(userId, imGroupChatList);

        return ListUtils.union(imPrivateChatDescriptors, imGroupChatDescriptors).stream()
                .sorted(Comparator.comparing(
                        ImUserChatDescriptor::getActiveTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public List<ImGroupChat> createGroupChats(List<GroupMember> newMembers) {
        return CollectionUtils.emptyIfNull(newMembers).stream()
                .distinct()
                .map(member -> ImGroupChat.builder()
                        .id(new ImChatId(snowflakeId.next()))
                        .groupId(member.getGroupId())
                        .userId(member.getUserId())
                        .type(ImChatType.GROUP)
                        .unreadMessageCount(0)
                        .build())
                .collect(Collectors.toList());
    }

    public GroupChatMembership createGroupChatMembership(List<GroupMember> newMembers) {
        List<ImGroupChat> groupChats = this.createGroupChats(newMembers);
        GroupId groupId = CollectionUtils.emptyIfNull(newMembers).stream()
                .findFirst()
                .map(GroupMember::getGroupId)
                .orElseThrow(() -> new IllegalArgumentException("群成员不能为空"));
        return new GroupChatMembership(groupId, groupChats);
    }

    public GroupChatMembership findGroupChatMembership(GroupId groupId) {
        return new GroupChatMembership(groupId, imGroupChatRepository.find(groupId));
    }

    public GroupChatJoin joinGroupChat(GroupId groupId, List<GroupMember> newMembers) {
        List<ImGroupChat> newChats = this.createGroupChats(newMembers);
        List<ImGroupChat> groupChats = ListUtils.union(imGroupChatRepository.find(groupId), newChats);
        return new GroupChatJoin(groupId, groupChats, newChats);
    }

    public ImPrivateChat createHiddenPrivateChat(UserId userId, UserId peerUserId) {
        ImPrivateChat privateChat = ImPrivateChat.builder()
                .id(new ImChatId(snowflakeId.next()))
                .userId(userId)
                .peerUserId(peerUserId)
                .type(ImChatType.PRIVATE)
                .build();
        privateChat.hide();
        return privateChat;
    }

    public Optional<ImPrivateChat> prepareHiddenPrivateChat(UserId userId, UserId peerUserId) {
        if (imPrivateChatRepository.contain(userId, peerUserId)) {
            return Optional.empty();
        }
        return Optional.of(this.createHiddenPrivateChat(userId, peerUserId));
    }

    public void enterChat(ImChat chat) {
        UserId userId = chat.getUserId();
        Session session = sessionRepository.find(userId)
                .orElseThrow(() -> new AuthException("用户尚未登陆"));
        if (!session.isSignIn()) {
            throw new AuthException("用户尚未登陆");
        }

        session.onEnterChat(chat.getId());
        sessionRepository.save(session);
    }

    public void exitChat(UserId userId) {
        Session session = sessionRepository.find(userId)
                .orElseThrow(() -> new AuthException("用户尚未登陆"));
        if (!session.isSignIn()) {
            throw new AuthException("用户尚未登陆");
        }

        session.onExitChat();
        sessionRepository.save(session);
    }

    private List<ImUserChatDescriptor> buildPrivateChatDescriptors(UserId userId, List<ImPrivateChat> imPrivateChatList) {
        if (CollectionUtils.isEmpty(imPrivateChatList)) {
            return Collections.emptyList();
        }
        imPrivateChatList = imPrivateChatList.stream()
                .filter(ImChat::isVisible)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(imPrivateChatList)) {
            return Collections.emptyList();
        }

        List<UserId> friendUserIds = FunctionUtils.mappingList(imPrivateChatList, ImPrivateChat::getPeerUserId);
        List<Friend> friendList = friendRepository.find(userId, friendUserIds);
        Map<UserId, Friend> friendMap = FunctionUtils.mappingMap(friendList, Friend::getFriendUserId, Function.identity());

        List<ImChatId> chatIds = FunctionUtils.mappingList(imPrivateChatList, ImPrivateChat::getId);
        List<ImMessage> chatLastMessages = imPrivateChatRepository.findLastMessageList(chatIds, userId);
        Map<Long, ImMessage> chatLastMessageMap = FunctionUtils.mappingMap(
                chatLastMessages, message -> message.getId().getValue(), Function.identity());

        return imPrivateChatList.stream()
                .map(imPrivateChat -> {
                    UserId friendUserId = imPrivateChat.getPeerUserId();
                    ImUserChatDescriptor descriptor = new ImUserChatDescriptor();
                    descriptor.setChatId(imPrivateChat.getId());
                    descriptor.setChatName(this.obtainFriendChatName(friendMap.get(friendUserId)));
                    descriptor.setChatType(imPrivateChat.getType());
                    descriptor.setActiveTime(imPrivateChat.getActiveTime());
                    Long lastMessageId = FunctionUtils.mappingOrNull(imPrivateChat.getLastMessageId(), ImMessageId::getValue);
                    descriptor.setChatLastMessage(chatLastMessageMap.get(lastMessageId));
                    return descriptor;
                }).collect(Collectors.toList());
    }

    private List<ImUserChatDescriptor> buildGroupChatDescriptors(UserId userId, List<ImGroupChat> imGroupChatList) {
        if (CollectionUtils.isEmpty(imGroupChatList)) {
            return Collections.emptyList();
        }
        imGroupChatList = imGroupChatList.stream()
                .filter(ImChat::isVisible)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(imGroupChatList)) {
            return Collections.emptyList();
        }

        List<GroupId> groupIds = FunctionUtils.mappingList(imGroupChatList, ImGroupChat::getGroupId);
        List<Group> groups = groupRepository.find(groupIds);
        Map<GroupId, Group> groupMap = FunctionUtils.mappingMap(groups, Group::getId, Function.identity());

        List<ImChatId> chatIds = FunctionUtils.mappingList(imGroupChatList, ImGroupChat::getId);
        List<ImMessage> chatLastMessages = imGroupChatRepository.findLastMessageList(chatIds, userId);
        Map<ImChatId, ImMessage> chatLastMessageMap = FunctionUtils.mappingMap(
                chatLastMessages, message -> ((ImGroupInboxMessage) message).getChatId(), Function.identity());

        return imGroupChatList.stream()
                .filter(imGroupChat -> {
                    Group group = groupMap.get(imGroupChat.getGroupId());
                    return group != null && !group.isDismissed();
                })
                .map(imGroupChat -> {
                    Group group = groupMap.get(imGroupChat.getGroupId());
                    ImUserChatDescriptor descriptor = new ImUserChatDescriptor();
                    descriptor.setChatId(imGroupChat.getId());
                    descriptor.setChatName(this.obtainGroupChatName(group, imGroupChat.getGroupAlias()));
                    descriptor.setChatType(imGroupChat.getType());
                    descriptor.setActiveTime(imGroupChat.getActiveTime());
                    descriptor.setChatLastMessage(chatLastMessageMap.get(imGroupChat.getId()));
                    return descriptor;
                }).collect(Collectors.toList());
    }

    /**
     * 获取好友的聊天名称
     *
     * @param friend 好友
     * @return 聊天名称
     */
    public ImChatName obtainFriendChatName(Friend friend) {
        if (friend == null) {
            return null;
        }
        return new ImChatName(friend.displayName().getValue());
    }

    public ImChatName obtainGroupChatName(Group group, GroupAlias imGroupAlias) {
        if (group == null) {
            return null;
        }
        if (imGroupAlias != null) {
            return new ImChatName(imGroupAlias.getValue());
        } else {
            return new ImChatName(group.getName().getValue());
        }
    }


    public void ensureBelongsTo(ImChat chat, UserId userId) {
        if (!chat.belongsTo(userId)) {
            throw new BusinessException("非法开启聊天");
        }
    }
}
