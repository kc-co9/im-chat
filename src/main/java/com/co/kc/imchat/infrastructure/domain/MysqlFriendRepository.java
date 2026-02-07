package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.user.UserId;
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

@Repository
@RequiredArgsConstructor
public class MysqlFriendRepository implements FriendRepository {
    private final DbUserService dbUserService;
    private final DbFriendService dbFriendService;

    @Override
    public List<Friend> find(UserId userId) {
        List<DbFriend> dbFriendList = dbFriendService.getListByUserId(userId.getValue());
        if (CollectionUtils.isEmpty(dbFriendList)) {
            return Collections.emptyList();
        }
        List<Long> userIds = FunctionUtils.mappingList(dbFriendList, DbFriend::getUserId);
        List<DbUser> dbUserList = dbUserService.getListByUserIds(userIds);
        return FriendDomainTransformer.INSTANCE.friendListFrom(dbFriendList, dbUserList);
    }

    @Override
    public Friend find(UserId userId, UserId friendUserId) {
        DbFriend dbFriend = dbFriendService.getByUserIdAndFriendUserId(userId.getValue(), friendUserId.getValue()).orElse(null);
        if (dbFriend == null) {
            return null;
        }
        DbUser dbUser = dbUserService.getByUserId(dbFriend.getUserId()).orElse(null);
        if (dbUser == null) {
            return null;
        }
        return FriendDomainTransformer.INSTANCE.friendFrom(dbFriend, dbUser);
    }

    @Override
    public void save(Friend friend) {
        DbFriend dbFriend = FriendDbTransformer.INSTANCE.dbFriendFrom(friend);
        dbFriendService.saveOrUpdate(dbFriend);
    }

    @Override
    public void remove(Friend friend) {
        dbFriendService.removeByUserIdAndFriendUserId(friend.getUserId().getValue(), friend.getFriendUserId().getValue());
    }
}
