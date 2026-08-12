package com.co.kc.imchat.service.social.infrastructure.domain.repository;

import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.friend.model.FriendEdge;
import com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.service.social.infrastructure.mybatis.enums.DbFriendStatus;
import com.co.kc.imchat.service.social.infrastructure.mybatis.service.DbFriendService;
import com.co.kc.imchat.service.social.transformer.db.FriendDbTransformer;
import com.co.kc.imchat.service.social.transformer.domain.FriendDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class MysqlFriendRepository implements FriendRepository {
    private final DbFriendService dbFriendService;

    @Override
    public List<Friend> find(UserId userId) {
        List<DbFriend> dbFriendList = dbFriendService.getListByUserId(userId.value());
        return this.buildFriends(dbFriendList);
    }

    @Override
    public List<Friend> find(UserId userId, List<UserId> friendUserIds) {
        if (CollectionUtils.isEmpty(friendUserIds)) {
            return Collections.emptyList();
        }
        List<Long> friendUserIdValueList = FunctionUtils.mappingList(friendUserIds, UserId::value);
        List<DbFriend> dbFriendList = dbFriendService.getListByUserIdAndFriendUserIds(userId.value(), friendUserIdValueList);
        return this.buildFriends(dbFriendList);
    }

    @Override
    public Optional<Friend> find(FriendEdge edge) {
        return dbFriendService.getByUserIdAndFriendUserId(edge.userId().value(), edge.friendUserId().value())
                .map(FriendDomainTransformer.INSTANCE::friendFrom);
    }

    @Override
    public boolean contain(UserId userId, UserId friendUserId) {
        return dbFriendService.isExist(dbFriendService.getQueryWrapper()
                .select(DbFriend::getId)
                .eq(DbFriend::getUserId, userId.value())
                .eq(DbFriend::getFriendUserId, friendUserId.value()));
    }

    @Override
    public boolean isFriendshipActive(UserId userId, UserId friendUserId) {
        return dbFriendService.isExist(dbFriendService.getQueryWrapper()
                .select(DbFriend::getId)
                .eq(DbFriend::getUserId, userId.value())
                .eq(DbFriend::getFriendUserId, friendUserId.value())
                .eq(DbFriend::getFriendStatus, DbFriendStatus.NORMAL));
    }

    @Override
    public void save(Friend friend) {
        DbFriend dbFriend = FriendDbTransformer.INSTANCE.dbFriendFrom(friend);
        dbFriendService.saveOrUpdate(dbFriend);
    }

    @Override
    public void remove(Friend friend) {
        dbFriendService.removeByUserIdAndFriendUserId(
                friend.getUserId().value(), friend.getFriendUserId().value());
    }

    private List<Friend> buildFriends(List<DbFriend> dbFriendList) {
        if (CollectionUtils.isEmpty(dbFriendList)) {
            return Collections.emptyList();
        }
        return FriendDomainTransformer.INSTANCE.friendListFrom(dbFriendList);
    }

}
