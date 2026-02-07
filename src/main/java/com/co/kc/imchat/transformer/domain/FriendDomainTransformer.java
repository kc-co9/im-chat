package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendAlias;
import com.co.kc.imchat.domain.friend.FriendId;
import com.co.kc.imchat.domain.friend.FriendStatus;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbFriendStatus;
import com.co.kc.imchat.support.utils.FunctionUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mapper
public interface FriendDomainTransformer {
    FriendDomainTransformer INSTANCE = Mappers.getMapper(FriendDomainTransformer.class);

    default List<Friend> friendListFrom(List<DbFriend> dbFriendList, List<DbUser> dbUserList) {
        Map<Long, DbUser> userIdEntityMap = FunctionUtils.mappingMap(dbUserList, DbUser::getUserId, Function.identity());
        return dbFriendList.stream()
                .map(dbFriend -> INSTANCE.friendFrom(dbFriend, userIdEntityMap.get(dbFriend.getUserId())))
                .collect(Collectors.toList());
    }

    default Friend friendFrom(DbFriend dbFriend, DbUser dbUser) {
        Friend friend = new Friend();
        friend.setId(new FriendId(new UserId(dbFriend.getUserId()), new UserId(dbFriend.getFriendUserId())));
        friend.setUserId(new UserId(dbFriend.getUserId()));
        friend.setFriendUserId(new UserId(dbFriend.getFriendUserId()));
        friend.setStatus(INSTANCE.friendStatusFrom(dbFriend.getFriendStatus()));
        friend.setCreateTime(dbFriend.getCreateTime());
        friend.setIncrId(dbFriend.getId());
        if (StringUtils.isNotBlank(dbFriend.getFriendAlias())) {
            friend.setAlias(new FriendAlias(dbFriend.getFriendAlias()));
        } else {
            friend.setAlias(new FriendAlias(dbUser.getUsername()));
        }
        return friend;
    }

    @ValueMappings(value = {
            @ValueMapping(source = "NONE", target = "NORMAL"),
            @ValueMapping(source = "NORMAL", target = "NORMAL"),
            @ValueMapping(source = "BLOCKED", target = "BLOCKED"),
            @ValueMapping(source = "DELETED", target = "DELETED")
    })
    FriendStatus friendStatusFrom(DbFriendStatus status);
}
