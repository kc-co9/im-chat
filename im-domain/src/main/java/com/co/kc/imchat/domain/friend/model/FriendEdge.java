package com.co.kc.imchat.domain.friend.model;

import com.co.kc.imchat.common.exception.BusinessException;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.domain.user.model.UserId;

/**
 * 值对象：好友关系边。
 */
public record FriendEdge(UserId userId, UserId friendUserId) {
    public FriendEdge {
        AssertUtils.allDomainPropNotNull("用户不能为空", userId, friendUserId);
        if (userId.equals(friendUserId)) {
            throw new BusinessException("不能操作自己");
        }
    }
}
