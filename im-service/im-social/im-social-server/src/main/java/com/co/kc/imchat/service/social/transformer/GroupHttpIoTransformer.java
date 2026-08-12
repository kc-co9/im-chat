package com.co.kc.imchat.service.social.transformer;

import com.co.kc.imchat.service.social.model.cqrs.dto.group.GroupDetailDTO;
import com.co.kc.imchat.service.social.model.cqrs.dto.group.GroupItemDTO;
import com.co.kc.imchat.service.social.model.io.group.GroupDetailResponse;
import com.co.kc.imchat.service.social.model.io.group.GroupListResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface GroupHttpIoTransformer {
    GroupHttpIoTransformer INSTANCE = Mappers.getMapper(GroupHttpIoTransformer.class);

    List<GroupListResponse.GroupItem> groupItemListFrom(List<GroupItemDTO> groupList);

    @Mapping(target = "ownerId", ignore = true)
    GroupListResponse.GroupItem groupItemFrom(GroupItemDTO groupItem);

    GroupDetailResponse groupDetailResponseFrom(GroupDetailDTO detail);
}
