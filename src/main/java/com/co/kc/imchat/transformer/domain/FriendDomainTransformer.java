package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendStatus;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbFriendStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface FriendDomainTransformer {
    FriendDomainTransformer INSTANCE = Mappers.getMapper(FriendDomainTransformer.class);

    List<Friend> friendListFrom(List<DbFriend> friends);

    @Mappings(value = {
            @Mapping(target = "incrId", source = "id"),
            @Mapping(target = "id.userId.value", source = "userId"),
            @Mapping(target = "id.friendUserId.value", source = "friendUserId"),
            @Mapping(target = "userId.value", source = "userId"),
            @Mapping(target = "friendUserId.value", source = "friendUserId"),
            @Mapping(target = "alias.value", source = "friendAlias"),
            @Mapping(target = "status", source = "friendStatus"),
            @Mapping(target = "createTime", source = "createTime")
    })
    Friend friendFrom(DbFriend dbFriend);

    @ValueMappings(value = {
            @ValueMapping(source = "NONE", target = "NORMAL"),
            @ValueMapping(source = "NORMAL", target = "NORMAL"),
            @ValueMapping(source = "BLOCKED", target = "BLOCKED"),
            @ValueMapping(source = "DELETED", target = "DELETED")
    })
    FriendStatus friendStatusFrom(DbFriendStatus status);
}
