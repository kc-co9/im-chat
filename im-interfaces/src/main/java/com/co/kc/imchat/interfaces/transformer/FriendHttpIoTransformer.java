package com.co.kc.imchat.interfaces.transformer;

import com.co.kc.imchat.application.model.cqrs.dto.friend.FriendDetailDTO;
import com.co.kc.imchat.application.model.cqrs.dto.friend.FriendItemDTO;
import com.co.kc.imchat.application.model.cqrs.dto.friend.FriendSearchDTO;
import com.co.kc.imchat.interfaces.model.io.friend.FriendDetailResponse;
import com.co.kc.imchat.interfaces.model.io.friend.FriendListResponse;
import com.co.kc.imchat.interfaces.model.io.friend.FriendSearchResponse;
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
            @Mapping(target = "displayName", source = "displayName"),
            @Mapping(target = "createTime", source = "createTime")})
    FriendListResponse.FriendItem friendItemFrom(FriendItemDTO friend);

    @Mappings(value = {
            @Mapping(target = "userId", source = "userId"),
            @Mapping(target = "username", source = "username"),
            @Mapping(target = "alias", source = "alias"),
            @Mapping(target = "createTime", source = "createTime")})
    FriendDetailResponse friendDetailResponseFrom(FriendDetailDTO friendDetailDTO);

    List<FriendSearchResponse.SearchItem> searchListFrom(List<FriendSearchDTO> friendSearchList);

    @Mappings(value = {
            @Mapping(target = "userId", source = "userId"),
            @Mapping(target = "username", source = "username"),
    })
    FriendSearchResponse.SearchItem searchDtoFrom(FriendSearchDTO friendSearchDTO);
}
