package com.co.kc.imchat.domain.group.model;

import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

@Getter
public class GroupMemberInvitation {
    private final Group group;
    private final List<GroupMember> newMembers;

    public GroupMemberInvitation(Group group, List<GroupMember> newMembers) {
        if (group == null) {
            throw new IllegalArgumentException("group is null");
        }
        if (CollectionUtils.isEmpty(newMembers)) {
            throw new IllegalArgumentException("newMembers is empty");
        }
        this.group = group;
        this.newMembers = newMembers;
    }
}
