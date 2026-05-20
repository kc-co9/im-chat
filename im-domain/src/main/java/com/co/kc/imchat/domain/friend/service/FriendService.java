package com.co.kc.imchat.domain.friend.service;

import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.model.FriendEdge;
import com.co.kc.imchat.domain.friend.model.FriendId;
import com.co.kc.imchat.domain.friend.model.FriendStatus;
import com.co.kc.imchat.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.common.exception.BusinessException;
import com.co.kc.imchat.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class FriendService {
    private final FriendRepository friendRepository;

    public Friend newFriend(UserId userId, User friendUser) {
        FriendId friendId = new FriendId(userId, friendUser.getId());
        return new Friend(friendId, userId, friendUser.getId(),
                friendUser.getUsername(), null, FriendStatus.NORMAL, LocalDateTime.now());
    }

    public List<Friend> addFriend(User user, User friendUser) {
        if (user.getId().equals(friendUser.getId())) {
            throw new BusinessException("不能添加自己为好友");
        }
        Optional<Friend> friend = friendRepository.find(new FriendEdge(user.getId(), friendUser.getId()));
        Optional<Friend> peerFriend = friendRepository.find(new FriendEdge(friendUser.getId(), user.getId()));
        if (friend.isPresent() && peerFriend.isPresent()) {
            throw new BusinessException("好友已存在");
        }

        List<Friend> friends = new ArrayList<>();
        if (!friend.isPresent()) {
            friends.add(this.newFriend(user.getId(), friendUser));
        }
        if (!peerFriend.isPresent()) {
            friends.add(this.newFriend(friendUser.getId(), user));
        }
        return friends;
    }

    public void ensureFriendshipActive(UserId userId, UserId peerUserId) {
        if (!friendRepository.isFriendshipActive(userId, peerUserId)
                || !friendRepository.isFriendshipActive(peerUserId, userId)) {
            throw new NotFoundException("好友不存在");
        }
    }
}
