package com.co.kc.imchat.service.social.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.user.model.UserId;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：群成员唯一标识。
 */
public record MemberId(GroupId groupId, UserId userId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public MemberId {
        AssertUtils.allDomainPropNotNull("参数不能为空", groupId, userId);
    }
}
