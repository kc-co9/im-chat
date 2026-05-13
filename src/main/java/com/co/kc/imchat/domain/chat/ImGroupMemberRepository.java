package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface ImGroupMemberRepository {

    List<ImGroupMember> find(ImGroupId groupId);

    ImGroupMember find(ImGroupId groupId, UserId userId);

    void saveAll(List<ImGroupMember> members);
}
