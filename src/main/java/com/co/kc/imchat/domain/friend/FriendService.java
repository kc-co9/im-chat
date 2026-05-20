package com.co.kc.imchat.domain.friend;

import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
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
