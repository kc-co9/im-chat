package com.co.kc.imchat.domain.friend;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface FriendRepository {
    List<Friend> find(UserId userId);

    List<Friend> find(UserId userId, List<UserId> friendUserIds);

    Friend find(UserId userId, UserId friendUserId);

    void save(Friend friend);

    void remove(Friend friend);

}
