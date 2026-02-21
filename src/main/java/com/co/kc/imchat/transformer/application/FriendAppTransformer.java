package com.co.kc.imchat.transformer.application;

import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.model.cqrs.dto.friend.FriendDetailDTO;
import com.co.kc.imchat.model.cqrs.dto.friend.FriendItemDTO;
import com.co.kc.imchat.model.cqrs.dto.friend.FriendSearchDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface FriendAppTransformer {
    FriendAppTransformer INSTANCE = Mappers.getMapper(FriendAppTransformer.class);

    List<FriendItemDTO> friendItemListFrom(List<Friend> friends);

    default FriendItemDTO friendItemDtoFrom(Friend friend) {
        FriendItemDTO friendItemDTO = new FriendItemDTO();
        friendItemDTO.setUserId(friend.getFriendUserId().getValue());
        friendItemDTO.setStatus(friend.getStatus());
        friendItemDTO.setCreateTime(friend.getCreateTime());
        friendItemDTO.setDisplayName(friend.displayName().getValue());
        return friendItemDTO;
    }

    @Mappings(value = {
            @Mapping(target = "userId", source = "user.id.value"),
            @Mapping(target = "username", source = "user.username.value"),
            @Mapping(target = "alias", source = "friend.friendAlias.value"),
            @Mapping(target = "status", source = "friend.status"),
            @Mapping(target = "createTime", source = "friend.createTime")
    })
    FriendDetailDTO friendDetailDtoFrom(User user, Friend friend);

    @Mappings(value = {
            @Mapping(target = "userId", source = "id.value"),
            @Mapping(target = "username", source = "username.value")
    })
    FriendSearchDTO friendSearchDtoFrom(User user);
}
