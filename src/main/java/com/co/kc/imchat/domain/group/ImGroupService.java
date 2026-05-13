package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.message.ImMessageRecipient;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.support.utils.FunctionUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ImGroupService {
    private final ImGroupMemberRepository imGroupMemberRepository;
    private final ImGroupChatRepository imGroupChatRepository;

    public ImGroupRoster createRoster(ImGroupId groupId, UserId ownerId, List<UserId> memberIds) {
        Set<UserId> groupUserIds = new LinkedHashSet<>();
        groupUserIds.add(ownerId);
        groupUserIds.addAll(CollectionUtils.emptyIfNull(memberIds));

        LocalDateTime now = LocalDateTime.now();
        List<ImGroupMember> members = groupUserIds.stream()
                .map(userId -> newGroupMember(groupId, userId, now))
                .collect(Collectors.toList());
        return new ImGroupRoster(groupId, members);
    }

    public ImGroupRoster findGroup(ImGroupId groupId) {
        List<ImGroupMember> members = imGroupMemberRepository.find(groupId);
        return new ImGroupRoster(groupId, members);
    }

    public List<ImGroupChat> findMemberChats(ImGroupId groupId) {
        List<ImGroupMember> members = imGroupMemberRepository.find(groupId);
        List<UserId> memberUserIds = members.stream()
                .map(ImGroupMember::getUserId)
                .collect(Collectors.toList());
        return imGroupChatRepository.findByUserIdsAndGroupId(groupId, memberUserIds);
    }

    public List<ImMessageRecipient> findMessageRecipients(
            ImGroupId groupId, Function<ImGroupChat, Boolean> chattingChecker) {
        return findMemberChats(groupId).stream()
                .map(chat -> new ImMessageRecipient(chat, chattingChecker.apply(chat)))
                .collect(Collectors.toList());
    }

    public List<ImUserGroupDescriptor> describeUserGroups(UserId userId, List<ImGroup> groups) {
        if (userId == null || CollectionUtils.isEmpty(groups)) {
            return Collections.emptyList();
        }

        Set<ImGroupId> groupIdSet = FunctionUtils.mappingSet(groups, ImGroup::getId);
        List<ImGroupChat> groupChats = imGroupChatRepository.find(userId, groupIdSet);

        Map<ImGroupId, ImGroupChat> groupChatMap = FunctionUtils.mappingMap(groupChats, ImGroupChat::getGroupId, Function.identity());
        return groups.stream()
                .map(group -> new ImUserGroupDescriptor(group.getId(), group.getName(), groupChatMap.get(group.getId())))
                .filter(descriptor -> descriptor.getChat() != null)
                .collect(Collectors.toList());
    }

    private ImGroupMember newGroupMember(ImGroupId groupId, UserId userId, LocalDateTime joinTime) {
        return ImGroupMember.builder()
                .id(new ImGroupMemberId(groupId, userId))
                .groupId(groupId)
                .userId(userId)
                .joinTime(joinTime)
                .build();
    }

}
