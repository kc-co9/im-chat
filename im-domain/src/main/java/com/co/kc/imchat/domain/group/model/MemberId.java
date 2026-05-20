package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.domain.user.model.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class MemberId {
    private final GroupId groupId;
    private final UserId userId;

    public MemberId(GroupId groupId, UserId userId) {
        if (groupId == null || userId == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        this.groupId = groupId;
        this.userId = userId;
    }
}
