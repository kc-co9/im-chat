package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository {

    List<GroupMember> find(GroupId groupId);

    Optional<GroupMember> find(GroupId groupId, UserId userId);

    boolean contain(GroupId groupId, UserId userId);

    void save(GroupMember member);

    void save(List<GroupMember> members);

    void remove(GroupMember groupMember);
}
