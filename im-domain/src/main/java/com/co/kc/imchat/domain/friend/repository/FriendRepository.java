package com.co.kc.imchat.domain.friend.repository;

import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.model.FriendEdge;
import com.co.kc.imchat.domain.user.model.UserId;

import java.util.List;
import java.util.Optional;

public interface FriendRepository {
    List<Friend> find(UserId userId);

    List<Friend> find(UserId userId, List<UserId> friendUserIds);

    Optional<Friend> find(FriendEdge edge);

    boolean contain(UserId userId, UserId friendUserId);

    boolean isFriendshipActive(UserId userId, UserId friendUserId);

    void save(Friend friend);

    void remove(Friend friend);

}
