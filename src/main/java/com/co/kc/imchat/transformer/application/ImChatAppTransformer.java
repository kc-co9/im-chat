package com.co.kc.imchat.transformer.application;

import com.co.kc.imchat.domain.chat.ImUserChatDescriptor;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatItemDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ImChatAppTransformer {
    ImChatAppTransformer INSTANCE = Mappers.getMapper(ImChatAppTransformer.class);

    List<ImChatItemDTO> imChatListFrom(List<ImUserChatDescriptor> imUserChatDescriptors);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "chatName", source = "chatName.value"),
            @Mapping(target = "chatType", source = "chatType"),
            @Mapping(target = "lastMessageType", source = "chatLastMessage.content.type"),
            @Mapping(target = "lastMessageContent", source = "chatLastMessage.content.value"),
    })
    ImChatItemDTO imChatItemDtoFrom(ImUserChatDescriptor imUserChatDescriptors);
}
