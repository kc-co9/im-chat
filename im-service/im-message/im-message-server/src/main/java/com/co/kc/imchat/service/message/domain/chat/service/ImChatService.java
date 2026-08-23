package com.co.kc.imchat.service.message.domain.chat.service;

import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.common.domain.group.model.GroupAlias;
import com.co.kc.imchat.service.message.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.service.message.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.service.message.domain.chat.model.GroupChatJoin;
import com.co.kc.imchat.service.message.domain.chat.model.GroupChatMember;
import com.co.kc.imchat.service.message.domain.chat.model.GroupChatMembership;
import com.co.kc.imchat.service.message.domain.chat.model.ImChat;
import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatName;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatType;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImUserChatDescriptor;
import com.co.kc.imchat.service.message.domain.message.model.ImMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.social.model.FriendDisplay;
import com.co.kc.imchat.service.message.domain.social.model.GroupSummary;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.user.model.UserId;
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
import java.util.stream.Collectors;

/**
 * IM聊天-领域服务
 */
@RequiredArgsConstructor
public class ImChatService {
    private final ImPrivateChatRepository imPrivateChatRepository;
    private final ImGroupChatRepository imGroupChatRepository;
    private final SnowflakeId snowflakeId;

    public Optional<ImPrivateChat> getPeerChat(ImPrivateChat imChat) {
        if (imChat == null) {
            throw new IllegalArgumentException("聊天会话传入为空");
        }
        return imPrivateChatRepository.find(imChat.getPeerUserId(), imChat.getUserId());
    }

    public List<ImGroupChat> createGroupChats(List<GroupChatMember> newMembers) {
        return CollectionUtils.emptyIfNull(newMembers).stream()
                .distinct()
                .map(member -> ImGroupChat.builder()
                        .id(new ImChatId(snowflakeId.next()))
                        .groupId(member.groupId())
                        .userId(member.userId())
                        .type(ImChatType.GROUP)
                        .unreadMessageCount(0)
                        .build())
                .collect(Collectors.toList());
    }

    public GroupChatMembership createGroupChatMembership(List<GroupChatMember> newMembers) {
        List<ImGroupChat> groupChats = this.createGroupChats(newMembers);
        GroupId groupId = CollectionUtils.emptyIfNull(newMembers).stream()
                .findFirst()
                .map(GroupChatMember::groupId)
                .orElseThrow(() -> new IllegalArgumentException("群成员不能为空"));
        return new GroupChatMembership(groupId, groupChats);
    }

    public GroupChatMembership findGroupChatMembership(GroupId groupId) {
        return new GroupChatMembership(groupId, imGroupChatRepository.find(groupId));
    }

    public GroupChatJoin joinGroupChat(GroupId groupId, List<GroupChatMember> newMembers) {
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

    public void ensureBelongsTo(ImChat chat, UserId userId) {
        if (!chat.belongsTo(userId)) {
            throw new BusinessException("非法开启聊天");
        }
    }

    public List<ImPrivateChat> visiblePrivateChats(List<ImPrivateChat> privateChats) {
        return CollectionUtils.emptyIfNull(privateChats).stream()
                .filter(ImChat::isVisible)
                .toList();
    }

    public List<ImGroupChat> visibleGroupChats(List<ImGroupChat> groupChats) {
        return CollectionUtils.emptyIfNull(groupChats).stream()
                .filter(ImChat::isVisible)
                .toList();
    }

    public List<ImUserChatDescriptor> mergeChatDescriptors(List<ImUserChatDescriptor> privateChatDescriptors,
                                                           List<ImUserChatDescriptor> groupChatDescriptors) {
        return ListUtils.union(
                        privateChatDescriptors == null ? Collections.emptyList() : privateChatDescriptors,
                        groupChatDescriptors == null ? Collections.emptyList() : groupChatDescriptors)
                .stream()
                .sorted(Comparator.comparing(
                        ImUserChatDescriptor::activeTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<ImUserChatDescriptor> describePrivateChats(List<ImPrivateChat> privateChats,
                                                           Map<Long, FriendDisplay> friendMap,
                                                           Map<Long, ImMessage> chatLastMessageMap) {
        return CollectionUtils.emptyIfNull(privateChats).stream()
                .map(chat -> {
                    Long lastMessageId = FunctionUtils.mappingOrNull(chat.getLastMessageId(), ImMessageId::value);
                    return new ImUserChatDescriptor(
                            chat.getId(),
                            obtainFriendChatName(friendMap.get(chat.getPeerUserId().value())),
                            chat.getType(),
                            chatLastMessageMap.get(lastMessageId),
                            chat.getActiveTime());
                })
                .toList();
    }

    public List<ImUserChatDescriptor> describeGroupChats(List<ImGroupChat> groupChats,
                                                         Map<Long, GroupSummary> groupMap,
                                                         Map<ImChatId, ImMessage> chatLastMessageMap) {
        return CollectionUtils.emptyIfNull(groupChats).stream()
                .filter(chat -> {
                    GroupSummary group = groupMap.get(chat.getGroupId().value());
                    return group != null && group.active();
                })
                .map(chat -> {
                    GroupSummary group = groupMap.get(chat.getGroupId().value());
                    return new ImUserChatDescriptor(
                            chat.getId(),
                            obtainGroupChatName(group, chat.getGroupAlias()),
                            chat.getType(),
                            chatLastMessageMap.get(chat.getId()),
                            chat.getActiveTime());
                })
                .toList();
    }

    private ImChatName obtainFriendChatName(FriendDisplay friend) {
        if (friend == null || friend.displayName() == null) {
            return null;
        }
        return new ImChatName(friend.displayName());
    }

    private ImChatName obtainGroupChatName(GroupSummary group, GroupAlias groupAlias) {
        if (group == null) {
            return null;
        }
        if (groupAlias != null) {
            return new ImChatName(groupAlias.value());
        }
        return new ImChatName(group.name());
    }
}
