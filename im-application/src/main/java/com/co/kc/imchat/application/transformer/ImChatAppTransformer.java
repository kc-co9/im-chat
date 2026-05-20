package com.co.kc.imchat.application.transformer;

import com.co.kc.imchat.domain.chat.model.ImUserChatDescriptor;
import com.co.kc.imchat.application.model.cqrs.dto.im.ImChatItemDTO;
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
            @Mapping(target = "lastMessageContent", source = "chatLastMessage.visibleContent"),
            @Mapping(target = "lastMessageTime", source = "chatLastMessage.sendTime"),
    })
    ImChatItemDTO imChatItemDtoFrom(ImUserChatDescriptor imUserChatDescriptors);
}
