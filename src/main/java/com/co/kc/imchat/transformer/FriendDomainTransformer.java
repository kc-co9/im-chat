package com.co.kc.imchat.transformer;

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
            @Mapping(target = "userId.value", source = "userId"),
            @Mapping(target = "friendId.value", source = "friendId"),
            @Mapping(target = "alias.value", source = "friendAlias"),
            @Mapping(target = "status", source = "friendStatus"),
            @Mapping(target = "createTime", source = "createTime")
    })
    Friend friendFrom(DbFriend dbFriend);
    
    @ValueMappings({
            @ValueMapping(source = "NONE", target = "NORMAL"),
            @ValueMapping(source = "NORMAL", target = "NORMAL"),
            @ValueMapping(source = "BLOCK", target = "BLOCKED"),
            @ValueMapping(source = "DELETE", target = "DELETED")
    })
    FriendStatus mapFriendStatus(DbFriendStatus status);
}
