package com.co.kc.imchat.service.social.domain.group.repository;

import com.co.kc.imchat.service.social.domain.group.model.Group;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.user.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * 资源库：群组。
 */
public interface GroupRepository {

    Optional<Group> find(GroupId groupId);

    List<Group> find(UserId userId);

    List<Group> find(List<GroupId> groupIds);

    void save(Group group);
}
