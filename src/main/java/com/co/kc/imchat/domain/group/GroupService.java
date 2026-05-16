package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.message.ImMessageRecipient;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserRepository;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class GroupService {
    private final GroupMemberRepository groupMemberRepository;
    private final ImGroupChatRepository imGroupChatRepository;
    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    public GroupRoster createRoster(GroupId groupId, UserId ownerId, List<UserId> memberIds) {
        Set<UserId> groupUserIds = new LinkedHashSet<>();
        groupUserIds.add(ownerId);
        groupUserIds.addAll(CollectionUtils.emptyIfNull(memberIds));

        LocalDateTime now = LocalDateTime.now();
        List<GroupMember> members = groupUserIds.stream()
                .map(userId -> newGroupMember(groupId, userId, now))
                .collect(Collectors.toList());
        return new GroupRoster(groupId, members);
    }

    public GroupRoster findGroup(GroupId groupId) {
        List<GroupMember> members = groupMemberRepository.find(groupId);
        return new GroupRoster(groupId, members);
    }

    public List<ImGroupChat> findMemberChats(GroupId groupId) {
        List<GroupMember> members = groupMemberRepository.find(groupId);
        List<UserId> memberUserIds = members.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toList());
        return imGroupChatRepository.findByUserIdsAndGroupId(groupId, memberUserIds);
    }

    public List<ImMessageRecipient> findMessageRecipients(
            GroupId groupId, Predicate<ImGroupChat> chattingChecker) {
        return findMemberChats(groupId).stream()
                .map(chat -> new ImMessageRecipient(chat, chattingChecker.test(chat)))
                .collect(Collectors.toList());
    }

    public List<UserGroupDescriptor> describeUserGroups(UserId userId, List<Group> groups) {
        if (userId == null || CollectionUtils.isEmpty(groups)) {
            return Collections.emptyList();
        }

        Set<GroupId> groupIdSet = FunctionUtils.mappingSet(groups, Group::getId);
        List<ImGroupChat> groupChats = imGroupChatRepository.find(userId, groupIdSet);

        Map<GroupId, ImGroupChat> groupChatMap = FunctionUtils.mappingMap(groupChats, ImGroupChat::getGroupId, Function.identity());
        return groups.stream()
                .map(group -> new UserGroupDescriptor(
                        group.getId(), group.getName(), groupChatMap.get(group.getId()), group.getMemberCount()))
                .filter(descriptor -> descriptor.getChat() != null)
                .collect(Collectors.toList());
    }

    public List<MemberDescriptor> describeGroupMembers(UserId userId, List<GroupMember> members) {
        if (userId == null || CollectionUtils.isEmpty(members)) {
            return Collections.emptyList();
        }

        List<UserId> memberIds = FunctionUtils.mappingList(members, GroupMember::getUserId);
        if (CollectionUtils.isEmpty(memberIds)) {
            return Collections.emptyList();
        }

        Map<UserId, Friend> friendMap = FunctionUtils.mappingMap(friendRepository.find(userId, memberIds), Friend::getFriendUserId, Function.identity());
        Map<UserId, User> userMap = FunctionUtils.mappingMap(userRepository.find(memberIds), User::getId, Function.identity());
        return members.stream()
                .map(member -> new MemberDescriptor(
                        member.getUserId(),
                        decideDisplayName(member, friendMap.get(member.getUserId()), userMap.get(member.getUserId())),
                        member.getJoinTime()))
                .collect(Collectors.toList());
    }

    private MemberDisplayName decideDisplayName(GroupMember member, Friend friend, User user) {
        if (friend != null) {
            return new MemberDisplayName(friend.displayName().getValue());
        }
        if (member.getUserAlias() != null) {
            return new MemberDisplayName(member.getUserAlias().getValue());
        }
        return user == null || user.getUsername() == null ? null : new MemberDisplayName(user.getUsername().getValue());
    }

    private GroupMember newGroupMember(GroupId groupId, UserId userId, LocalDateTime joinTime) {
        return GroupMember.builder()
                .id(new MemberId(groupId, userId))
                .groupId(groupId)
                .userId(userId)
                .joinTime(joinTime)
                .build();
    }

}
