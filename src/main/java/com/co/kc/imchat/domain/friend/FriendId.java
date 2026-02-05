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
        this.userId = userId;
        this.friendUserId = friendUserId;
    }
}
