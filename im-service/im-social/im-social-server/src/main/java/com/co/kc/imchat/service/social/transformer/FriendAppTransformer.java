package com.co.kc.imchat.service.social.transformer;

import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.friend.model.FriendProfile;
import com.co.kc.imchat.service.social.facade.dto.FriendDisplayDTO;
import com.co.kc.imchat.service.social.facade.dto.FriendRelationCheckDTO;
import com.co.kc.imchat.service.social.model.cqrs.dto.friend.FriendDetailDTO;
import com.co.kc.imchat.service.social.model.cqrs.dto.friend.FriendItemDTO;
import com.co.kc.imchat.service.social.model.cqrs.dto.friend.FriendSearchDTO;
import com.co.kc.imchat.service.social.domain.account.model.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface FriendAppTransformer {
    FriendAppTransformer INSTANCE = Mappers.getMapper(FriendAppTransformer.class);

    List<FriendItemDTO> friendItemListFrom(List<FriendProfile> friends);

    default FriendItemDTO friendItemDtoFrom(FriendProfile friend) {
        FriendItemDTO friendItemDTO = new FriendItemDTO();
        friendItemDTO.setUserId(friend.friendUserId().value());
        friendItemDTO.setStatus(friend.status());
        friendItemDTO.setCreateTime(friend.createTime());
        friendItemDTO.setDisplayName(friend.displayName().value());
        return friendItemDTO;
    }

    default FriendDetailDTO friendDetailDtoFrom(UserProfile profile, Friend friend) {
        FriendDetailDTO dto = new FriendDetailDTO();
        dto.setUserId(profile.userId().value());
        dto.setUsername(profile.username());
        dto.setAlias(friend.getFriendAlias() == null ? null : friend.getFriendAlias().value());
        dto.setStatus(friend.getStatus());
        dto.setCreateTime(friend.getCreateTime());
        return dto;
    }

    default FriendSearchDTO friendSearchDtoFrom(UserProfile profile) {
        FriendSearchDTO dto = new FriendSearchDTO();
        dto.setUserId(profile.userId().value());
        dto.setUsername(profile.username());
        return dto;
    }

    default FriendDisplayDTO friendDisplayDtoFrom(FriendProfile friend) {
        return new FriendDisplayDTO(friend.friendUserId().value(), friend.displayName().value());
    }

    default FriendRelationCheckDTO friendRelationCheckDtoFrom(boolean active) {
        return new FriendRelationCheckDTO(active);
    }
}
