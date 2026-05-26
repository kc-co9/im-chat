package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.List;

/**
 * 值对象：群成员邀请结果。
 */
public record GroupMemberInvitation(Group group, List<GroupMember> newMembers) {
    public GroupMemberInvitation {
        AssertUtils.domainPropNotNull("群组不能为空", group);
        AssertUtils.domainPropNotEmpty("新群成员不能为空", newMembers);
    }
}
