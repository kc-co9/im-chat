package com.kim.omgchat.domain.friend;

import com.kim.omgchat.domain.user.UserId;

import java.util.List;

public interface FriendRepository {
    List<Friend> find(UserId userId);

    Friend find(UserId userId, UserId friendId);

    void save(Friend friend);

    void remove(Friend friend);

}
