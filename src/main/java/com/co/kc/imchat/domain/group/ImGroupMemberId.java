package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.user.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ImGroupMemberId {
    private final ImGroupId groupId;
    private final UserId userId;

    public ImGroupMemberId(ImGroupId groupId, UserId userId) {
        if (groupId == null || userId == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        this.groupId = groupId;
        this.userId = userId;
    }
}
