package com.co.kc.imchat.interfaces.transformer;

import com.co.kc.imchat.application.model.cqrs.dto.group.GroupDetailDTO;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupItemDTO;
import com.co.kc.imchat.interfaces.model.io.group.GroupDetailResponse;
import com.co.kc.imchat.interfaces.model.io.group.GroupListResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface GroupHttpIoTransformer {
    GroupHttpIoTransformer INSTANCE = Mappers.getMapper(GroupHttpIoTransformer.class);

    List<GroupListResponse.GroupItem> groupItemListFrom(List<GroupItemDTO> groupList);

    GroupListResponse.GroupItem groupItemFrom(GroupItemDTO groupItem);

    GroupDetailResponse groupDetailResponseFrom(GroupDetailDTO detail);
}
