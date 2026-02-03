package com.kim.omgchat.infrastructure.domain;

import com.kim.omgchat.domain.friend.Friend;
import com.kim.omgchat.domain.friend.FriendRepository;
import com.kim.omgchat.domain.user.UserId;
import com.kim.omgchat.infrastructure.mybatis.entity.DbFriend;
import com.kim.omgchat.infrastructure.mybatis.service.DbFriendService;
import com.kim.omgchat.transformer.FriendDomainTransformer;
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
