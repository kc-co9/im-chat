package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface ImGroupRepository {

    ImGroup find(ImGroupId groupId);

    List<ImGroup> find(UserId userId);

    List<ImGroup> find(List<ImGroupId> groupIds);

    void save(ImGroup group);
}
