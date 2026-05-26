package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.domain.user.model.UserId;

/**
 * 值对象：群成员唯一标识。
 */
public record MemberId(GroupId groupId, UserId userId) {
    public MemberId {
        AssertUtils.allDomainPropNotNull("参数不能为空", groupId, userId);
    }
}
