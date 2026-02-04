package com.co.kc.imchat.domain.friend;

import com.co.kc.imchat.domain.user.UserId;

import java.time.LocalDateTime;

public class FriendService {
    public Friend addFriend(UserId userId, UserId friendId) {
        return new Friend(userId, friendId, null, FriendStatus.NORMAL, LocalDateTime.now());
    }
}
