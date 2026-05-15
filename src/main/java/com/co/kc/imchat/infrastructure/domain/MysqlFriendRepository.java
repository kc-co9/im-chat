package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.BaseEntity;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.infrastructure.mybatis.service.DbFriendService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbUserService;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.db.FriendDbTransformer;
import com.co.kc.imchat.transformer.domain.FriendDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MysqlFriendRepository implements FriendRepository {
    private final DbUserService dbUserService;
    private final DbFriendService dbFriendService;

    @Override
    public List<Friend> find(UserId userId) {
        List<DbFriend> dbFriendList = dbFriendService.getListByUserId(userId.getValue());
        return this.buildFriends(dbFriendList);
    }

    @Override
    public List<Friend> find(UserId userId, List<UserId> friendUserIds) {
        if (CollectionUtils.isEmpty(friendUserIds)) {
            return Collections.emptyList();
        }
        List<Long> friendUserIdValueList = FunctionUtils.mappingList(friendUserIds, UserId::getValue);
        List<DbFriend> dbFriendList = dbFriendService.getListByUserIdAndFriendUserIds(userId.getValue(), friendUserIdValueList);
        return this.buildFriends(dbFriendList);
    }

    @Override
    public Optional<Friend> find(UserId userId, UserId friendUserId) {
        return dbFriendService.getByUserIdAndFriendUserId(userId.getValue(), friendUserId.getValue())
                .flatMap(dbFriend -> dbUserService.getByUserId(dbFriend.getFriendUserId())
                        .map(dbFriendUser -> FriendDomainTransformer.INSTANCE.friendFrom(dbFriend, dbFriendUser)));
    }

    @Override
    public boolean contain(UserId userId, UserId friendUserId) {
        return dbFriendService.isExist(dbFriendService.getQueryWrapper()
                .select(BaseEntity::getId)
                .eq(DbFriend::getUserId, userId.getValue())
                .eq(DbFriend::getFriendUserId, friendUserId.getValue()));
    }

    @Override
    public void save(Friend friend) {
        DbFriend dbFriend = FriendDbTransformer.INSTANCE.dbFriendFrom(friend);
        dbFriendService.saveOrUpdate(dbFriend);
    }

    @Override
    public void remove(UserId userId, UserId friendUserId) {
        dbFriendService.removeByUserIdAndFriendUserId(userId.getValue(), friendUserId.getValue());
    }

    private List<Friend> buildFriends(List<DbFriend> dbFriendList) {
        if (CollectionUtils.isEmpty(dbFriendList)) {
            return Collections.emptyList();
        }
        List<Long> friendUserIds = FunctionUtils.mappingList(dbFriendList, DbFriend::getFriendUserId);
        List<DbUser> dbFriendUserList = dbUserService.getListByUserIds(friendUserIds);
        return FriendDomainTransformer.INSTANCE.friendListFrom(dbFriendList, dbFriendUserList);
    }

}
