package com.co.kc.imchat.domain.friend.model;

import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.common.exception.BusinessException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class FriendEdge {
    private final UserId userId;
    private final UserId friendUserId;

    public FriendEdge(UserId userId, UserId friendUserId) {
        if (userId == null || friendUserId == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
        if (userId.equals(friendUserId)) {
            throw new BusinessException("不能操作自己");
        }
        this.userId = userId;
        this.friendUserId = friendUserId;
    }
}
