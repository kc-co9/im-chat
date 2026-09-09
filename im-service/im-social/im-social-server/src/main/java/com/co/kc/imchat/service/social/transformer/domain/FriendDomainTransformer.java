package com.co.kc.imchat.service.social.transformer.domain;

import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.friend.model.FriendAlias;
import com.co.kc.imchat.service.social.domain.friend.model.FriendId;
import com.co.kc.imchat.service.social.domain.friend.model.FriendStatus;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.service.social.infrastructure.mybatis.enums.DbFriendStatus;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface FriendDomainTransformer {
    FriendDomainTransformer INSTANCE = Mappers.getMapper(FriendDomainTransformer.class);

    default List<Friend> friendListFrom(List<DbFriend> dbFriendList) {
        return dbFriendList.stream()
                .map(INSTANCE::friendFrom)
                .toList();
    }

    default Friend friendFrom(DbFriend dbFriend) {
        Friend friend = new Friend();
        friend.setId(new FriendId(new UserId(dbFriend.getUserId()), new UserId(dbFriend.getFriendUserId())));
        friend.setUserId(new UserId(dbFriend.getUserId()));
        friend.setFriendUserId(new UserId(dbFriend.getFriendUserId()));
        friend.setStatus(INSTANCE.friendStatusFrom(dbFriend.getFriendStatus()));
        friend.setCreateTime(dbFriend.getCreateTime());
        friend.setPkId(dbFriend.getId());
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
