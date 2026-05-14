package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.group.Group;
import com.co.kc.imchat.domain.group.GroupAlias;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.group.GroupMember;
import com.co.kc.imchat.domain.group.GroupRepository;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.support.exception.AuthException;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IM聊天-领域服务
 */
@RequiredArgsConstructor
public class ImChatService {
    private final SnowflakeId snowflakeId;
    private final FriendRepository friendRepository;
    private final ImPrivateChatRepository imPrivateChatRepository;
    private final GroupRepository groupRepository;
    private final ImGroupChatRepository imGroupChatRepository;
    private final SessionRepository sessionRepository;

    public ImPrivateChat getPeerChat(ImPrivateChat imChat) {
        if (imChat == null) {
            throw new IllegalArgumentException("聊天会话传入为空");
        }
        return imPrivateChatRepository.find(imChat.getPeerUserId(), imChat.getUserId());
    }

    public List<ImUserChatDescriptor> getUserChatList(UserId userId) {
        List<ImPrivateChat> imPrivateChatList = imPrivateChatRepository.find(userId);
        List<ImUserChatDescriptor> imPrivateChatDescriptors = buildPrivateChatDescriptors(userId, imPrivateChatList);

        List<ImGroupChat> imGroupChatList = imGroupChatRepository.find(userId);
        List<ImUserChatDescriptor> imGroupChatDescriptors = buildGroupChatDescriptors(userId, imGroupChatList);

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

    public void enterChat(ImChat chat) {
        UserId userId = chat.getUserId();
        Session session = sessionRepository.find(userId);
        if (session == null || !session.isSignIn()) {
            throw new AuthException("用户尚未登陆");
        }

        session.onEnterChat(chat.getId());
        sessionRepository.save(session);
    }

    public void exitChat(UserId userId) {
        Session session = sessionRepository.find(userId);
        if (session == null || !session.isSignIn()) {
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
                    descriptor.setChatName(obtainFriendChatName(friendMap.get(friendUserId)));
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
            ImUserChatDescriptor descriptor = new ImUserChatDescriptor();
            descriptor.setChatId(imGroupChat.getId());
            descriptor.setChatName(obtainGroupChatName(groupMap.get(imGroupChat.getGroupId()), imGroupChat.getGroupAlias()));
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


}
