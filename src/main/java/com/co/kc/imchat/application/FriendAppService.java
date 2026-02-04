package com.co.kc.imchat.application;

import com.co.kc.imchat.common.exception.BusinessException;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.friend.FriendService;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserRepository;
import com.co.kc.imchat.model.cqrs.command.friend.FriendAddCmd;
import com.co.kc.imchat.model.cqrs.command.friend.FriendBlockCmd;
import com.co.kc.imchat.model.cqrs.command.friend.FriendDeleteCmd;
import com.co.kc.imchat.model.cqrs.command.friend.FriendUnblockCmd;
import com.co.kc.imchat.model.cqrs.dto.friend.FriendDetailDTO;
import com.co.kc.imchat.model.cqrs.dto.friend.FriendItemDTO;
import com.co.kc.imchat.model.cqrs.query.friend.FriendDetailQuery;
import com.co.kc.imchat.model.cqrs.query.friend.FriendListQuery;
import com.co.kc.imchat.transformer.FriendAppTransformer;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class FriendAppService {
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;

    private final FriendService friendService;

    public void addFriend(FriendAddCmd command) {
        UserId userId = new UserId(command.getUserId());
        UserId friendId = new UserId(command.getFriendId());

        Friend friend = friendRepository.find(userId, friendId);
        if (friend != null) {
            throw new BusinessException("好友已存在");
        }

        Friend newFriend = friendService.addFriend(userId, friendId);
        Friend newPeerFriend = friendService.addFriend(friendId, userId);
        friendRepository.save(newFriend);
        friendRepository.save(newPeerFriend);
    }

    public void blockFriend(FriendBlockCmd command) {
        UserId userId = new UserId(command.getUserId());
        UserId friendId = new UserId(command.getFriendId());

        Friend friend = friendRepository.find(userId, friendId);
        if (friend == null) {
            throw new NotFoundException("好友不存在");
        }

        friend.block();
        friendRepository.save(friend);
    }

    public void unblockFriend(FriendUnblockCmd command) {
        UserId userId = new UserId(command.getUserId());
        UserId friendId = new UserId(command.getFriendId());

        Friend friend = friendRepository.find(userId, friendId);
        if (friend == null) {
            throw new NotFoundException("好友不存在");
        }

        friend.unblock();
        friendRepository.save(friend);
    }

    public void deleteFriend(FriendDeleteCmd command) {
        UserId userId = new UserId(command.getUserId());
        UserId friendId = new UserId(command.getFriendId());

        Friend friend = friendRepository.find(userId, friendId);
        if (friend == null) {
            throw new NotFoundException("好友不存在");
        }

        friendRepository.remove(friend);
    }

    public List<FriendItemDTO> getFriendList(FriendListQuery query) {
        UserId userId = new UserId(query.getUserId());
        List<Friend> friends = friendRepository.find(userId);
        return FriendAppTransformer.INSTANCE.friendItemListFrom(friends);
    }

    public FriendDetailDTO getFriendDetail(FriendDetailQuery query) {
        UserId userId = new UserId(query.getUserId());
        UserId friendId = new UserId(query.getFriendId());
        Friend friend = friendRepository.find(userId, friendId);
        if (friend == null) {
            throw new NotFoundException("好友不存在");
        }
        User user = userRepository.find(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return FriendAppTransformer.INSTANCE.friendDetailDtoFrom(user, friend);
    }
}
