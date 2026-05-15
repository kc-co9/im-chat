package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {

    Optional<Group> find(GroupId groupId);

    List<Group> find(UserId userId);

    List<Group> find(List<GroupId> groupIds);

    void save(Group group);
}
