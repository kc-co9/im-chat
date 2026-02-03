package com.kim.omgchat.transformer;

import com.kim.omgchat.model.cqrs.dto.friend.FriendDetailDTO;
import com.kim.omgchat.model.cqrs.dto.friend.FriendItemDTO;
import com.kim.omgchat.model.io.friend.FriendDetailResponse;
import com.kim.omgchat.model.io.friend.FriendListResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface FriendHttpIoTransformer {
    FriendHttpIoTransformer INSTANCE = Mappers.getMapper(FriendHttpIoTransformer.class);

    List<FriendListResponse.FriendItem> friendListFrom(List<FriendItemDTO> friends);

    @Mappings(value = {
            @Mapping(target = "userId", source = "userId"),
            @Mapping(target = "alias", source = "alias"),
            @Mapping(target = "createTime", source = "createTime")})
    FriendListResponse.FriendItem friendItemFrom(FriendItemDTO friend);

    @Mappings(value = {
            @Mapping(target = "userId", source = "userId"),
            @Mapping(target = "username", source = "username"),
            @Mapping(target = "alias", source = "alias"),
            @Mapping(target = "createTime", source = "createTime")})
    FriendDetailResponse friendDetailResponseFrom(FriendDetailDTO friendDetailDTO);
}
