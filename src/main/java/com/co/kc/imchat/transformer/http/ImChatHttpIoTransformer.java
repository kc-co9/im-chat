package com.co.kc.imchat.transformer.http;

import com.co.kc.imchat.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.model.io.chat.ImChatListResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ImChatHttpIoTransformer {
    ImChatHttpIoTransformer INSTANCE = Mappers.getMapper(ImChatHttpIoTransformer.class);

    List<ImChatListResponse.ImChatItem> imChatItemListFrom(List<ImChatItemDTO> imChatList);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "chatName", source = "chatName"),
            @Mapping(target = "chatType", source = "chatType")
    })
    ImChatListResponse.ImChatItem imChatItemFrom(ImChatItemDTO imChatItem);
}
