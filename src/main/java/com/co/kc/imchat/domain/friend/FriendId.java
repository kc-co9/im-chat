package com.co.kc.imchat.domain.friend;

import com.co.kc.imchat.domain.user.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 好友唯一标识-值对象
 */
@Getter
@EqualsAndHashCode
public class FriendId {
    private final UserId userId;
    private final UserId friendUserId;

    public FriendId(UserId userId, UserId friendUserId) {
        if (userId == null || friendUserId == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
        if (userId.equals(friendUserId)) {
            throw new IllegalArgumentException("用户不能是自己");
        }
        this.userId = userId;
        this.friendUserId = friendUserId;
    }
}
