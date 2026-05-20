package com.co.kc.imchat.infrastructure.transformer.db;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.model.FriendStatus;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbFriendStatus;
import org.mapstruct.factory.Mappers;

@Mapper
public interface FriendDbTransformer {
    FriendDbTransformer INSTANCE = Mappers.getMapper(FriendDbTransformer.class);

    @Mappings(value = {
            @Mapping(target = "id", source = "pkId"),
            @Mapping(target = "userId", source = "userId.value"),
            @Mapping(target = "friendUserId", source = "friendUserId.value"),
            @Mapping(target = "friendAlias", source = "friendAlias.value"),
            @Mapping(target = "friendStatus", source = "status"),
            @Mapping(target = "createTime", source = "createTime"),
            @Mapping(target = "updateTime", ignore = true),
            @Mapping(target = "isDeleted", ignore = true)
    })
    DbFriend dbFriendFrom(Friend friend);


    @ValueMappings(value = {
            @ValueMapping(source = "NORMAL", target = "NORMAL"),
            @ValueMapping(source = "BLOCKED", target = "BLOCKED"),
            @ValueMapping(source = "DELETED", target = "DELETED")
    })
    DbFriendStatus dbFriendStatus(FriendStatus status);
}
