package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.user.UserEmail;
import com.co.kc.imchat.model.cqrs.dto.friend.FriendSearchDTO;
import com.co.kc.imchat.model.cqrs.query.friend.FriendSearchQuery;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
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
import com.co.kc.imchat.transformer.application.FriendAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class FriendAppService {
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final ImPrivateChatRepository imPrivateChatRepository;

    private final FriendService friendService;
    private final ImChatService imChatService;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void addFriend(FriendAddCmd command) {
        UserId userId = new UserId(command.getUserId());
        UserId friendUserId = new UserId(command.getFriendUserId());
        if (userId.equals(friendUserId)) {
            throw new BusinessException("不能添加自己为好友");
        }

        User user = userRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        User friendUser = userRepository.find(friendUserId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        if (friendRepository.contain(userId, friendUserId)) {
            throw new BusinessException("好友已存在");
        }

        Friend newFriend = friendService.newFriend(userId, friendUser);
        Friend newPeerFriend = friendService.newFriend(friendUserId, user);
        friendRepository.save(newFriend);
        friendRepository.save(newPeerFriend);
        imPrivateChatRepository.save(imChatService.createHiddenPrivateChat(userId, friendUserId));
        imPrivateChatRepository.save(imChatService.createHiddenPrivateChat(friendUserId, userId));
    }

    public void blockFriend(FriendBlockCmd command) {
        UserId userId = new UserId(command.getUserId());
        UserId friendUserId = new UserId(command.getFriendUserId());
        if (userId.equals(friendUserId)) {
            throw new BusinessException("不能拉黑自己");
        }

        Friend friend = friendRepository.find(userId, friendUserId)
                .orElseThrow(() -> new NotFoundException("好友不存在"));

        friend.block();
        friendRepository.save(friend);
    }

    public void unblockFriend(FriendUnblockCmd command) {
        UserId userId = new UserId(command.getUserId());
        UserId friendUserId = new UserId(command.getFriendUserId());
        if (userId.equals(friendUserId)) {
            throw new BusinessException("不能取消拉黑自己");
        }

        Friend friend = friendRepository.find(userId, friendUserId)
                .orElseThrow(() -> new NotFoundException("好友不存在"));

        friend.unblock();
        friendRepository.save(friend);
    }

    public void deleteFriend(FriendDeleteCmd command) {
        UserId userId = new UserId(command.getUserId());
        UserId friendUserId = new UserId(command.getFriendUserId());
        if (userId.equals(friendUserId)) {
            throw new BusinessException("不能删除自己");
        }

        if (!friendRepository.contain(userId, friendUserId)) {
            throw new NotFoundException("好友不存在");
        }
        friendRepository.remove(userId, friendUserId);
    }

    public List<FriendItemDTO> getFriendList(FriendListQuery query) {
        UserId userId = new UserId(query.getUserId());
        List<Friend> friends = friendRepository.find(userId);
        return FriendAppTransformer.INSTANCE.friendItemListFrom(friends);
    }

    public FriendDetailDTO getFriendDetail(FriendDetailQuery query) {
        UserId userId = new UserId(query.getUserId());
        UserId friendUserId = new UserId(query.getFriendUserId());
        Friend friend = friendRepository.find(userId, friendUserId)
                .orElseThrow(() -> new NotFoundException("好友不存在"));
        User user = userRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        return FriendAppTransformer.INSTANCE.friendDetailDtoFrom(user, friend);
    }

    public List<FriendSearchDTO> searchFriends(FriendSearchQuery query) {
        UserEmail email = new UserEmail(query.getEmail());
        return userRepository.find(email)
                .map(foundUser -> Collections.singletonList(FriendAppTransformer.INSTANCE.friendSearchDtoFrom(foundUser)))
                .orElse(Collections.emptyList());
    }
}
