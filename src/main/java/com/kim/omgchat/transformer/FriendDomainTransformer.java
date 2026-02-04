package com.kim.omgchat.transformer;

import com.kim.omgchat.domain.friend.Friend;
import com.kim.omgchat.domain.friend.FriendStatus;
import com.kim.omgchat.infrastructure.mybatis.entity.DbFriend;
import com.kim.omgchat.infrastructure.mybatis.enums.DbFriendStatus;
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
