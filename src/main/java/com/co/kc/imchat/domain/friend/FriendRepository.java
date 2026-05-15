package com.co.kc.imchat.domain.friend;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public interface FriendRepository {
    List<Friend> find(UserId userId);

    List<Friend> find(UserId userId, List<UserId> friendUserIds);

    Optional<Friend> find(UserId userId, UserId friendUserId);

    boolean contain(UserId userId, UserId friendUserId);

    void save(Friend friend);

    void remove(UserId userId, UserId friendUserId);

}
