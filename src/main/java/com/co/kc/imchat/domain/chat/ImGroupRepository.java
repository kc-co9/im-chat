package com.co.kc.imchat.domain.chat;

import java.util.List;

public interface ImGroupRepository {

    ImGroup find(ImGroupId groupId);

    List<ImGroup> find(List<ImGroupId> groupIds);

    void save(ImGroup group);
}
