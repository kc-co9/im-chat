package com.co.kc.imchat.transformer.http;

import com.co.kc.imchat.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatOpenDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupCreateDTO;
import com.co.kc.imchat.model.io.chat.ImChatListResponse;
import com.co.kc.imchat.model.io.chat.ImGroupChatOpenResponse;
import com.co.kc.imchat.model.io.chat.ImGroupCreateResponse;
import com.co.kc.imchat.model.io.chat.ImPrivateChatOpenResponse;
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

    ImPrivateChatOpenResponse imPrivateChatOpenResponseFrom(ImChatOpenDTO dto);

    ImGroupChatOpenResponse imGroupChatOpenResponseFrom(ImChatOpenDTO dto);

    ImGroupCreateResponse imGroupCreateResponseFrom(ImGroupCreateDTO dto);
}
