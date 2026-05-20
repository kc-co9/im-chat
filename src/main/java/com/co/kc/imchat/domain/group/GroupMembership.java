package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.support.exception.BusinessException;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class GroupMembership {
    public static final int MAX_MEMBER_COUNT = 500;

    private final GroupId groupId;
    private final List<GroupMember> members;

    public GroupMembership(GroupId groupId, List<GroupMember> members) {
        if (groupId == null) {
            throw new IllegalArgumentException("群组 ID 不能为空");
        }
        if (CollectionUtils.isEmpty(members)) {
            throw new IllegalArgumentException("群组成员不能为空");
        }
        if (members.size() > MAX_MEMBER_COUNT) {
            throw new IllegalArgumentException("群组成员数量不能超过 500 人");
        }
        this.groupId = groupId;
        this.members = members;
    }

    public List<GroupMember> invite(UserId inviterId, List<UserId> inviteeIds) {
        if (!contains(inviterId)) {
            throw new BusinessException("用户无此群组权限");
        }

        Set<UserId> groupMemberIds = members.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toSet());
        List<UserId> newInviteeIds = CollectionUtils.emptyIfNull(inviteeIds).stream()
                .filter(userId -> !groupMemberIds.contains(userId))
                .distinct()
                .collect(Collectors.toList());
        if (newInviteeIds.isEmpty()) {
            throw new BusinessException("没有可邀请的成员");
        }
        if (members.size() + newInviteeIds.size() > MAX_MEMBER_COUNT) {
            throw new BusinessException(String.format("群成员数量不能超过 %d 人", MAX_MEMBER_COUNT));
        }

        return newInviteeIds.stream()
                .map(userId ->
                        GroupMember.builder()
                                .id(new MemberId(groupId, userId))
                                .groupId(groupId)
                                .userId(userId)
                                .joinTime(LocalDateTime.now())
                                .build()
                )
                .collect(Collectors.toList());
    }

    private boolean contains(UserId userId) {
        return userId != null && members.stream().anyMatch(member -> member.isMember(userId));
    }

}
