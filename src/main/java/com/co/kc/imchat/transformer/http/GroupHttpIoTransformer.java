package com.co.kc.imchat.transformer.http;

import com.co.kc.imchat.model.cqrs.dto.im.ImGroupDetailDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupItemDTO;
import com.co.kc.imchat.model.io.chat.ImGroupDetailResponse;
import com.co.kc.imchat.model.io.chat.ImGroupListResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface GroupHttpIoTransformer {
    GroupHttpIoTransformer INSTANCE = Mappers.getMapper(GroupHttpIoTransformer.class);

    List<ImGroupListResponse.GroupItem> imGroupItemListFrom(List<ImGroupItemDTO> groupList);

    ImGroupListResponse.GroupItem imGroupItemFrom(ImGroupItemDTO groupItem);

    ImGroupDetailResponse imGroupDetailResponseFrom(ImGroupDetailDTO detail);
}
