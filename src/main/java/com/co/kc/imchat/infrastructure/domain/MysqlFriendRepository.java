package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.infrastructure.mybatis.service.DbFriendService;
import com.co.kc.imchat.transformer.FriendDomainTransformer;
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
    public Friend find(UserId userId, UserId friendId) {
        Optional<DbFriend> friend = dbFriendService.getByUserIdAndFriendId(userId.getValue(), friendId.getValue());
        return friend.map(FriendDomainTransformer.INSTANCE::friendFrom).orElse(null);
    }

    @Override
    public void save(Friend friend) {

    }

    @Override
    public void remove(Friend friend) {

    }
}
