package com.co.kc.imchat.service.social.domain.friend.service;

import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.friend.model.FriendEdge;
import com.co.kc.imchat.service.social.domain.friend.model.FriendId;
import com.co.kc.imchat.service.social.domain.friend.model.FriendStatus;
import com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.common.domain.user.model.UserId;
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

    public Friend newFriend(UserId userId, UserId friendUserId) {
        FriendId friendId = new FriendId(userId, friendUserId);
        return new Friend(friendId, userId, friendUserId, null, null, FriendStatus.NORMAL, LocalDateTime.now());
    }

    public List<Friend> addFriend(UserId userId, UserId friendUserId) {
        if (userId.equals(friendUserId)) {
            throw new BusinessException("不能添加自己为好友");
        }
        Optional<Friend> friend = friendRepository.find(new FriendEdge(userId, friendUserId));
        Optional<Friend> peerFriend = friendRepository.find(new FriendEdge(friendUserId, userId));
        if (friend.isPresent() && peerFriend.isPresent()) {
            throw new BusinessException("好友已存在");
        }

        List<Friend> friends = new ArrayList<>();
        if (!friend.isPresent()) {
            friends.add(this.newFriend(userId, friendUserId));
        }
        if (!peerFriend.isPresent()) {
            friends.add(this.newFriend(friendUserId, userId));
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
