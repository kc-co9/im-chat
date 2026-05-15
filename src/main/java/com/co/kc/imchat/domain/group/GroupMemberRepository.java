package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;
import java.util.Optional;
import java.util.Map;

public interface GroupMemberRepository {

    List<GroupMember> find(GroupId groupId);

    Optional<GroupMember> find(GroupId groupId, UserId userId);

    boolean contain(GroupId groupId, UserId userId);

    Map<GroupId, Integer> countByGroupIds(List<GroupId> groupIds);

    void saveAll(List<GroupMember> members);
}
