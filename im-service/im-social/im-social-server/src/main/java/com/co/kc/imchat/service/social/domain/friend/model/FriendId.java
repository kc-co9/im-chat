package com.co.kc.imchat.service.social.domain.friend.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.domain.user.model.UserId;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：好友唯一标识。
 */
public record FriendId(UserId userId, UserId friendUserId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public FriendId {
        AssertUtils.allDomainPropNotNull("用户不能为空", userId, friendUserId);
        AssertUtils.domainPropTrue("用户不能是自己", !userId.equals(friendUserId));
    }
}
