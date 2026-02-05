package com.co.kc.imchat.domain.friend;

import com.co.kc.imchat.domain.user.UserId;

import java.time.LocalDateTime;

public class FriendService {
    public Friend addFriend(UserId userId, UserId friendUserId) {
        FriendId friendId = new FriendId(userId, friendUserId);
        return new Friend(friendId, userId, friendUserId, null, FriendStatus.NORMAL, LocalDateTime.now());
    }
}
