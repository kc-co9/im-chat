package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface GroupRepository {

    Group find(GroupId groupId);

    List<Group> find(UserId userId);

    List<Group> find(List<GroupId> groupIds);

    void save(Group group);
}
