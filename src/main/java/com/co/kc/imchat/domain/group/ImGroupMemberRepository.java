package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;
import java.util.Map;

public interface ImGroupMemberRepository {

    List<ImGroupMember> find(ImGroupId groupId);

    ImGroupMember find(ImGroupId groupId, UserId userId);

    Map<ImGroupId, Integer> countByGroupIds(List<ImGroupId> groupIds);

    void saveAll(List<ImGroupMember> members);
}
