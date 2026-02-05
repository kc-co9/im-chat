package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.infrastructure.mybatis.service.DbFriendService;
import com.co.kc.imchat.transformer.db.FriendDbTransformer;
import com.co.kc.imchat.transformer.domain.FriendDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MysqlFriendRepository implements FriendRepository {
    private final DbFriendService dbFriendService;

    @Override
    public List<Friend> find(UserId userId) {
        List<DbFriend> friends = dbFriendService.getListByUserId(userId.getValue());
        return FriendDomainTransformer.INSTANCE.friendListFrom(friends);
    }

    @Override
    public Friend find(UserId userId, UserId friendUserId) {
        Optional<DbFriend> friend = dbFriendService.getByUserIdAndFriendUserId(userId.getValue(), friendUserId.getValue());
        return friend.map(FriendDomainTransformer.INSTANCE::friendFrom).orElse(null);
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
