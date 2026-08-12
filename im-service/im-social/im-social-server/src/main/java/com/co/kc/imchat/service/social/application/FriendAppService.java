package com.co.kc.imchat.service.social.application;

import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.social.facade.dto.FriendDisplayDTO;
import com.co.kc.imchat.service.social.facade.dto.FriendDisplaysDTO;
import com.co.kc.imchat.service.social.facade.dto.FriendRelationCheckDTO;
import com.co.kc.imchat.service.social.facade.params.FriendDisplaysGetParams;
import com.co.kc.imchat.service.social.facade.params.FriendRelationCheckParams;
import com.co.kc.imchat.service.social.domain.friend.model.FriendAlias;
import com.co.kc.imchat.service.social.domain.friend.model.FriendEdge;
import com.co.kc.imchat.service.social.model.cqrs.command.friend.FriendAliasChangeCmd;
import com.co.kc.imchat.service.social.model.cqrs.dto.friend.FriendSearchDTO;
import com.co.kc.imchat.service.social.model.cqrs.query.friend.FriendSearchQuery;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.service.social.domain.friend.service.FriendService;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.social.model.cqrs.command.friend.FriendAddCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.friend.FriendBlockCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.friend.FriendDeleteCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.friend.FriendUnblockCmd;
import com.co.kc.imchat.service.social.model.cqrs.dto.friend.FriendDetailDTO;
import com.co.kc.imchat.service.social.model.cqrs.dto.friend.FriendItemDTO;
import com.co.kc.imchat.service.social.model.cqrs.query.friend.FriendDetailQuery;
import com.co.kc.imchat.service.social.model.cqrs.query.friend.FriendListQuery;
import com.co.kc.imchat.service.social.transformer.FriendAppTransformer;
import com.co.kc.imchat.service.social.adapter.account.AccountAdapter;
import com.co.kc.imchat.service.social.adapter.message.MessageSocialAdapter;
import com.co.kc.imchat.service.social.domain.account.model.UserProfile;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class FriendAppService {
    private final AccountAdapter accountAdapter;
    private final FriendRepository friendRepository;
    private final FriendService friendService;
    private final MessageSocialAdapter messageSocialAdapter;
    private final AfterTransactionCommitTemplate afterTransactionCommitTemplate;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void addFriend(FriendAddCmd command) {
        UserId userId = new UserId(command.userId());
        UserId friendUserId = new UserId(command.friendUserId());

        accountAdapter.getUserProfile(userId.value());
        accountAdapter.getUserProfile(friendUserId.value());

        List<Friend> newFriends = friendService.addFriend(userId, friendUserId);
        for (Friend friend : newFriends) {
            friendRepository.save(friend);
        }
        afterTransactionCommitTemplate.execute(() -> newFriends.forEach(friend ->
                messageSocialAdapter.onFriendAdded(friend.getUserId().value(), friend.getFriendUserId().value())));
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void blockFriend(FriendBlockCmd command) {
        UserId userId = new UserId(command.userId());
        UserId friendUserId = new UserId(command.friendUserId());

        Friend friend = friendRepository.find(new FriendEdge(userId, friendUserId))
                .orElseThrow(() -> new NotFoundException("好友不存在"));
        friend.block();
        friendRepository.save(friend);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void unblockFriend(FriendUnblockCmd command) {
        UserId userId = new UserId(command.userId());
        UserId friendUserId = new UserId(command.friendUserId());

        Friend friend = friendRepository.find(new FriendEdge(userId, friendUserId))
                .orElseThrow(() -> new NotFoundException("好友不存在"));
        friend.unblock();
        friendRepository.save(friend);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void deleteFriend(FriendDeleteCmd command) {
        UserId userId = new UserId(command.userId());
        UserId friendUserId = new UserId(command.friendUserId());

        Friend friend = friendRepository.find(new FriendEdge(userId, friendUserId))
                .orElseThrow(() -> new NotFoundException("好友不存在"));
        friendRepository.remove(friend);

        afterTransactionCommitTemplate.execute(() ->
                messageSocialAdapter.onFriendRemoved(userId.value(), friendUserId.value()));
    }

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

    public FriendRelationCheckDTO checkFriendRelation(FriendRelationCheckParams params) {
        UserId userId = new UserId(params.userId());
        UserId friendUserId = new UserId(params.friendUserId());
        boolean active = friendRepository.isFriendshipActive(userId, friendUserId)
                && friendRepository.isFriendshipActive(friendUserId, userId);
        return FriendAppTransformer.INSTANCE.friendRelationCheckDtoFrom(active);
    }

    public FriendDisplaysDTO getFriendDisplays(FriendDisplaysGetParams params) {
        UserId userId = new UserId(params.userId());
        List<UserId> friendUserIds = FunctionUtils.mappingList(params.friendUserIds(), UserId::new);
        List<FriendDisplayDTO> friends = friendRepository.find(userId, friendUserIds).stream()
                .map(FriendAppTransformer.INSTANCE::friendDisplayDtoFrom)
                .toList();
        return FriendAppTransformer.INSTANCE.friendDisplaysDtoFrom(friends);
    }

    public FriendDetailDTO getFriendDetail(FriendDetailQuery query) {
        UserId userId = new UserId(query.userId());
        UserId friendUserId = new UserId(query.friendUserId());

        UserProfile friendUser = accountAdapter.getUserProfile(friendUserId.value());
        Friend friend = friendRepository.find(new FriendEdge(userId, friendUserId))
                .orElseThrow(() -> new NotFoundException("好友不存在"));
        return FriendAppTransformer.INSTANCE.friendDetailDtoFrom(friendUser, friend);
    }

    public List<FriendSearchDTO> searchFriends(FriendSearchQuery query) {
        return accountAdapter.findUserProfileByEmail(query.email())
                .map(FriendAppTransformer.INSTANCE::friendSearchDtoFrom)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }
}
