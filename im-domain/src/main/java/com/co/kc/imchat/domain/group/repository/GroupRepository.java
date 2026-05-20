package com.co.kc.imchat.domain.group.repository;

import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.user.model.UserId;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {

    Optional<Group> find(GroupId groupId);

    List<Group> find(UserId userId);

    List<Group> find(List<GroupId> groupIds);

    void save(Group group);
}
