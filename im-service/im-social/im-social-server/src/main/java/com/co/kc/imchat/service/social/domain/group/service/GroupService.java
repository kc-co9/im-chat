package com.co.kc.imchat.service.social.domain.group.service;

import com.co.kc.imchat.common.exception.BusinessException;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.service.social.domain.group.model.Group;
import com.co.kc.imchat.service.social.domain.group.model.GroupCreation;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.service.social.domain.group.model.GroupMemberDeparture;
import com.co.kc.imchat.service.social.domain.group.model.GroupMemberInvitation;
import com.co.kc.imchat.service.social.domain.group.model.GroupMembership;
import com.co.kc.imchat.service.social.domain.group.model.GroupName;
import com.co.kc.imchat.common.domain.group.model.GroupUserAlias;
import com.co.kc.imchat.common.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.common.domain.group.model.MemberDisplayName;
import com.co.kc.imchat.service.social.domain.group.model.MemberCount;
import com.co.kc.imchat.service.social.domain.group.model.MemberId;
import com.co.kc.imchat.service.social.domain.account.model.UserProfile;
import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.service.social.domain.group.repository.GroupRepository;
import com.co.kc.imchat.common.domain.user.model.UserId;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 群组领域服务。
 */
public class GroupService {
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;

    public GroupService(GroupMemberRepository groupMemberRepository, GroupRepository groupRepository) {
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
    }

    public GroupCreation createGroup(GroupId groupId, UserId ownerId, GroupName groupName, List<UserId> memberIds) {
        GroupMembership membership = this.createMembership(groupId, ownerId, memberIds);
        Group group = Group.builder()
                .id(groupId)
                .ownerId(ownerId)
                .name(groupName)
                .memberCount(new MemberCount(membership.members().size()))
                .build();
        return new GroupCreation(group, membership.members());
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

        GroupMember groupMember = groupMemberRepository.find(groupId, userId)
                .orElseThrow(() -> new NotFoundException("用户无此群组权限"));
        return new GroupMemberDeparture(group, groupMember);
    }

    public GroupMemberDeparture kickMember(GroupId groupId, UserId operatorId, UserId memberId) {
        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));
        group.kickMember(operatorId, memberId);

        GroupMember groupMember = groupMemberRepository.find(groupId, memberId)
                .orElseThrow(() -> new NotFoundException("用户无此群组权限"));
        return new GroupMemberDeparture(group, groupMember);
    }

    public GroupMember changeMemberAlias(GroupId groupId, UserId userId, GroupUserAlias userAlias) {
        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));
        this.ensureGroupMember(group, userId);

        GroupMember member = groupMemberRepository.find(groupId, userId)
                .orElseThrow(() -> new NotFoundException("成员不存在"));
        member.changeUserAlias(userAlias);
        return member;
    }

    public void ensureGroupMember(Group group, UserId userId) {
        group.ensureActive();

        if (!groupMemberRepository.contain(group.getId(), userId)) {
            throw new BusinessException("用户无此群组权限");
        }
    }

    public List<MemberDescriptor> describeMembers(List<GroupMember> members,
                                                  Map<UserId, Friend> friendMap,
                                                  Map<UserId, UserProfile> profileMap) {
        if (CollectionUtils.isEmpty(members)) {
            return Collections.emptyList();
        }
        Map<UserId, Friend> safeFriendMap = friendMap == null ? Collections.emptyMap() : friendMap;
        Map<UserId, UserProfile> safeProfileMap = profileMap == null ? Collections.emptyMap() : profileMap;
        return members.stream()
                .map(member -> new MemberDescriptor(
                        member.getUserId(),
                        decideDisplayName(
                                member,
                                safeFriendMap.get(member.getUserId()),
                                safeProfileMap.get(member.getUserId())),
                        member.getJoinTime()))
                .collect(Collectors.toList());
    }

    private MemberDisplayName decideDisplayName(GroupMember member, Friend friend, UserProfile profile) {
        if (friend != null) {
            return new MemberDisplayName(friend.displayName().value());
        }
        if (member.getUserAlias() != null) {
            return new MemberDisplayName(member.getUserAlias().value());
        }
        if (profile == null || profile.username() == null) {
            return null;
        }
        return new MemberDisplayName(profile.username());
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
