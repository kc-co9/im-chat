package com.co.kc.imchat.transformer;

import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.model.cqrs.dto.friend.FriendDetailDTO;
import com.co.kc.imchat.model.cqrs.dto.friend.FriendItemDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface FriendAppTransformer {
    FriendAppTransformer INSTANCE = Mappers.getMapper(FriendAppTransformer.class);

    List<FriendItemDTO> friendItemListFrom(List<Friend> friends);

    @Mappings(value = {
            @Mapping(target = "userId", source = "userId.value"),
            @Mapping(target = "alias", source = "alias.value"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "createTime", source = "createTime")
    })
    FriendItemDTO friendItemDtoFrom(Friend friend);

    @Mappings(value = {
            @Mapping(target = "userId", source = "user.id.value"),
            @Mapping(target = "username", source = "user.username.value"),
            @Mapping(target = "alias", source = "friend.alias.value"),
            @Mapping(target = "status", source = "friend.status"),
            @Mapping(target = "createTime", source = "friend.createTime")
    })
    FriendDetailDTO friendDetailDtoFrom(User user, Friend friend);
}
