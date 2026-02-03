package com.kim.omgchat.transformer;

import com.kim.omgchat.domain.chat.ImChat;
import com.kim.omgchat.model.cqrs.dto.im.ImChatItemDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ImChatAppTransformer {
    ImChatAppTransformer INSTANCE = Mappers.getMapper(ImChatAppTransformer.class);


    List<ImChatItemDTO> imChatListFrom(List<ImChat> chatList);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "id.value"),
            @Mapping(target = "chatName", source = "name.value"),
            @Mapping(target = "chatType", source = "type")})
    ImChatItemDTO imChatItemDtoFrom(ImChat chat);
}
