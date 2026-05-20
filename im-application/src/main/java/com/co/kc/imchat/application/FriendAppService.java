package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.service.ImChatService;
import com.co.kc.imchat.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.domain.friend.model.FriendAlias;
import com.co.kc.imchat.domain.friend.event.FriendAddedEvent;
import com.co.kc.imchat.domain.friend.event.FriendRemovedEvent;
import com.co.kc.imchat.domain.friend.model.FriendEdge;
import com.co.kc.imchat.domain.user.model.UserEmail;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendAliasChangeCmd;
import com.co.kc.imchat.application.model.cqrs.dto.friend.FriendSearchDTO;
import com.co.kc.imchat.application.model.cqrs.query.friend.FriendSearchQuery;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.application.support.lock.DistributeLockScene;
import com.co.kc.imchat.application.support.lock.annotation.DistributeLock;
import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.domain.friend.service.FriendService;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.repository.UserRepository;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendAddCmd;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendBlockCmd;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendDeleteCmd;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendUnblockCmd;
import com.co.kc.imchat.application.model.cqrs.dto.friend.FriendDetailDTO;
import com.co.kc.imchat.application.model.cqrs.dto.friend.FriendItemDTO;
import com.co.kc.imchat.application.model.cqrs.query.friend.FriendDetailQuery;
import com.co.kc.imchat.application.model.cqrs.query.friend.FriendListQuery;
import com.co.kc.imchat.application.support.event.DomainEventPublisher;
import com.co.kc.imchat.application.transformer.FriendAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class FriendAppService {
    private static final String FRIEND_PAIR_LOCK_KEY =
            "#LockKeys.userPair(#command.userId(), #command.friendUserId())";

    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final ImPrivateChatRepository imPrivateChatRepository;

    private final FriendService friendService;
    private final ImChatService imChatService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @DistributeLock(scene = DistributeLockScene.FRIEND_ADD, key = FRIEND_PAIR_LOCK_KEY)
    public void addFriend(FriendAddCmd command) {
        UserId userId = new UserId(command.userId());
        UserId friendUserId = new UserId(command.friendUserId());

        User user = userRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        User friendUser = userRepository.find(friendUserId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        List<Friend> newFriends = friendService.addFriend(user, friendUser);
        for (Friend friend : newFriends) {
            friendRepository.save(friend);
            domainEventPublisher.publish(new FriendAddedEvent(friend.getUserId(), friend.getFriendUserId()));
        }
    }

    public void onFriendAdded(FriendAddedEvent event) {
        imChatService.prepareHiddenPrivateChat(event.getUserId(), event.getFriendUserId())
                .ifPresent(imPrivateChatRepository::save);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @DistributeLock(scene = DistributeLockScene.FRIEND_ADD, key = FRIEND_PAIR_LOCK_KEY)
    public void blockFriend(FriendBlockCmd command) {
        UserId userId = new UserId(command.userId());
        UserId friendUserId = new UserId(command.friendUserId());

        Friend friend = friendRepository.find(new FriendEdge(userId, friendUserId))
                .orElseThrow(() -> new NotFoundException("好友不存在"));
        friend.block();
        friendRepository.save(friend);
    }

    @DistributeLock(scene = DistributeLockScene.FRIEND_ADD, key = FRIEND_PAIR_LOCK_KEY)
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void unblockFriend(FriendUnblockCmd command) {
        UserId userId = new UserId(command.userId());
        UserId friendUserId = new UserId(command.friendUserId());

        Friend friend = friendRepository.find(new FriendEdge(userId, friendUserId))
                .orElseThrow(() -> new NotFoundException("好友不存在"));
        friend.unblock();
        friendRepository.save(friend);
    }

    @DistributeLock(scene = DistributeLockScene.FRIEND_ADD, key = FRIEND_PAIR_LOCK_KEY)
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void deleteFriend(FriendDeleteCmd command) {
        UserId userId = new UserId(command.userId());
        UserId friendUserId = new UserId(command.friendUserId());

        Friend friend = friendRepository.find(new FriendEdge(userId, friendUserId))
                .orElseThrow(() -> new NotFoundException("好友不存在"));
        friendRepository.remove(friend);

        domainEventPublisher.publish(new FriendRemovedEvent(userId, friendUserId));
    }

    public void onFriendRemoved(FriendRemovedEvent event) {
        imPrivateChatRepository.remove(event.getUserId(), event.getFriendUserId());
    }

    @DistributeLock(scene = DistributeLockScene.FRIEND_ADD, key = FRIEND_PAIR_LOCK_KEY)
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void changeFriendAlias(FriendAliasChangeCmd command) {
        UserId userId = new UserId(command.userId());
        UserId friendUserId = new UserId(command.friendUserId());
        FriendAlias friendAlias = new FriendAlias(command.friendAlias());

        Friend friend = friendRepository.find(new FriendEdge(userId, friendUserId))
                .orElseThrow(() -> new NotFoundException("好友不存在"));
        friend.changeAlias(friendAlias);
        friendRepository.save(friend);
    }

    public List<FriendItemDTO> getFriendList(FriendListQuery query) {
        UserId userId = new UserId(query.userId());
        List<Friend> friends = friendRepository.find(userId);
        return FriendAppTransformer.INSTANCE.friendItemListFrom(friends);
    }

    public FriendDetailDTO getFriendDetail(FriendDetailQuery query) {
        UserId userId = new UserId(query.userId());
        UserId friendUserId = new UserId(query.friendUserId());

        User user = userRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        Friend friend = friendRepository.find(new FriendEdge(userId, friendUserId))
                .orElseThrow(() -> new NotFoundException("好友不存在"));
        return FriendAppTransformer.INSTANCE.friendDetailDtoFrom(user, friend);
    }

    public List<FriendSearchDTO> searchFriends(FriendSearchQuery query) {
        UserEmail email = new UserEmail(query.email());
        return userRepository.find(email)
                .map(FriendAppTransformer.INSTANCE::friendSearchDtoFrom)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }
}
