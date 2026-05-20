package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.message.ImMessageRecipient;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserRepository;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
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
    private final GroupRepository groupRepository;
    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    public GroupCreation createGroup(GroupId groupId, UserId ownerId, GroupName groupName, List<UserId> memberIds) {
        GroupMembership membership = this.createMembership(groupId, ownerId, memberIds);
        Group group = Group.builder()
                .id(groupId)
                .type(ImChatType.GROUP)
                .ownerId(ownerId)
                .name(groupName)
                .memberCount(new MemberCount(membership.getMembers().size()))
                .build();
        return new GroupCreation(group, membership.getMembers());
    }

    public GroupMembership createMembership(GroupId groupId, UserId ownerId, List<UserId> memberIds) {
        Set<UserId> groupUserIds = new LinkedHashSet<>();
        groupUserIds.add(ownerId);
        groupUserIds.addAll(CollectionUtils.emptyIfNull(memberIds));

        LocalDateTime now = LocalDateTime.now();
        List<GroupMember> members = groupUserIds.stream()
                .map(userId -> this.newGroupMember(groupId, userId, now))
                .collect(Collectors.toList());
        return new GroupMembership(groupId, members);
    }

    public GroupMemberInvitation inviteMembers(GroupId groupId, UserId inviterId, List<UserId> inviteeIds) {
        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));
        group.ensureActive();

        List<GroupMember> members = groupMemberRepository.find(groupId);
        GroupMembership membership = new GroupMembership(groupId, members);

        List<GroupMember> newInvitees = membership.invite(inviterId, inviteeIds);
        group.changeMemberCount(group.getMemberCount().increase(newInvitees.size()));

        return new GroupMemberInvitation(group, newInvitees);
    }

    public Group transferOwner(GroupId groupId, UserId operatorId, UserId newOwnerId) {
        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));

        this.ensureGroupMember(group, operatorId);
        this.ensureGroupMember(group, newOwnerId);

        group.transferOwner(operatorId, newOwnerId);
        return group;
    }

    public GroupMemberDeparture leaveGroup(GroupId groupId, UserId userId) {
        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));
        group.memberLeave(userId);

        GroupMember groupMember = groupMemberRepository.find(groupId, userId).orElseThrow(() -> new NotFoundException("用户无此群组权限"));
        return new GroupMemberDeparture(group, groupMember);
    }

    public GroupMemberDeparture kickMember(GroupId groupId, UserId operatorId, UserId memberId) {
        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));
        group.kickMember(operatorId, memberId);

        GroupMember groupMember = groupMemberRepository.find(groupId, memberId).orElseThrow(() -> new NotFoundException("用户无此群组权限"));
        return new GroupMemberDeparture(group, groupMember);
    }

    public GroupMember changeMemberAlias(GroupId groupId, UserId userId, GroupUserAlias userAlias) {
        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));

        this.ensureGroupMember(group, userId);

        GroupMember member = groupMemberRepository.find(groupId, userId).orElseThrow(() -> new NotFoundException("成员不存在"));
        member.changeUserAlias(userAlias);
        return member;
    }

    public List<ImGroupChat> findMemberChats(GroupId groupId) {
        List<GroupMember> members = groupMemberRepository.find(groupId);
        List<UserId> memberIds = members.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toList());
        return imGroupChatRepository.find(groupId, memberIds);
    }

    public List<ImMessageRecipient> findMessageRecipients(
            GroupId groupId, Predicate<ImGroupChat> chattingChecker) {
        return this.findMemberChats(groupId).stream()
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
                        this.decideDisplayName(member, friendMap.get(member.getUserId()), userMap.get(member.getUserId())),
                        member.getJoinTime()))
                .collect(Collectors.toList());
    }

    public void ensureGroupMember(Group group, UserId userId) {
        group.ensureActive();

        if (!groupMemberRepository.contain(group.getId(), userId)) {
            throw new BusinessException("用户无此群组权限");
        }
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
