package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;
import java.util.Map;

public interface GroupMemberRepository {

    List<GroupMember> find(GroupId groupId);

    GroupMember find(GroupId groupId, UserId userId);

    Map<GroupId, Integer> countByGroupIds(List<GroupId> groupIds);

    void saveAll(List<GroupMember> members);
}
