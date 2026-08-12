package com.co.kc.imchat.service.social.domain.group.repository;

import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.common.domain.user.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * 资源库：群成员。
 */
public interface GroupMemberRepository {

    List<GroupMember> find(GroupId groupId);

    Optional<GroupMember> find(GroupId groupId, UserId userId);

    boolean contain(GroupId groupId, UserId userId);

    void save(GroupMember member);

    void save(List<GroupMember> members);

    void remove(GroupMember groupMember);
}
