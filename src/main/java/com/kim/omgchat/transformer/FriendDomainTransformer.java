package com.kim.omgchat.transformer;

import com.kim.omgchat.domain.friend.Friend;
import com.kim.omgchat.infrastructure.mybatis.entity.DbFriend;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
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
}
