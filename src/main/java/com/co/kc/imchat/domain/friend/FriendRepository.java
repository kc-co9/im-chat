package com.co.kc.imchat.domain.friend;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface FriendRepository {
    List<Friend> find(UserId userId);

    Friend find(UserId userId, UserId friendUserId);

    void save(Friend friend);

    void remove(Friend friend);

}
