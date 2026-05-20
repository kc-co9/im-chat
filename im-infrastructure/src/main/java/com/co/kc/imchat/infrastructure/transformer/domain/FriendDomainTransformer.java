package com.co.kc.imchat.infrastructure.transformer.domain;

import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.model.FriendAlias;
import com.co.kc.imchat.domain.friend.model.FriendId;
import com.co.kc.imchat.domain.friend.model.FriendStatus;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbFriendStatus;
import com.co.kc.imchat.common.utils.FunctionUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mapper
public interface FriendDomainTransformer {
    FriendDomainTransformer INSTANCE = Mappers.getMapper(FriendDomainTransformer.class);

    default List<Friend> friendListFrom(List<DbFriend> dbFriendList, List<DbUser> dbFriendUserList) {
        Map<Long, DbUser> friendUserIdEntityMap = FunctionUtils.mappingMap(dbFriendUserList, DbUser::getUserId, Function.identity());
        return dbFriendList.stream()
                .map(dbFriend -> INSTANCE.friendFrom(dbFriend, friendUserIdEntityMap.get(dbFriend.getFriendUserId())))
                .collect(Collectors.toList());
    }

    default Friend friendFrom(DbFriend dbFriend, DbUser dbFriendUser) {
        Friend friend = new Friend();
        friend.setId(new FriendId(new UserId(dbFriend.getUserId()), new UserId(dbFriend.getFriendUserId())));
        friend.setUserId(new UserId(dbFriend.getUserId()));
        friend.setFriendUserId(new UserId(dbFriend.getFriendUserId()));
        friend.setStatus(INSTANCE.friendStatusFrom(dbFriend.getFriendStatus()));
        friend.setCreateTime(dbFriend.getCreateTime());
        friend.setPkId(dbFriend.getId());
        friend.setFriendName(new UserName(dbFriendUser.getUsername()));
        if (StringUtils.isNotBlank(dbFriend.getFriendAlias())) {
            friend.setFriendAlias(new FriendAlias(dbFriend.getFriendAlias()));
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
