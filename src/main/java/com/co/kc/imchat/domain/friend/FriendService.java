package com.co.kc.imchat.domain.friend;

import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserId;

import java.time.LocalDateTime;

public class FriendService {
    public Friend newFriend(UserId userId, User friendUser) {
        FriendId friendId = new FriendId(userId, friendUser.getId());
        return new Friend(friendId, userId, friendUser.getId(),
                friendUser.getUsername(), null, FriendStatus.NORMAL, LocalDateTime.now());
    }
}
