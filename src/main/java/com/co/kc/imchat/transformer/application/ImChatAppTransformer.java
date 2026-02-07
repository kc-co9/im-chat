package com.co.kc.imchat.transformer.application;

import com.co.kc.imchat.domain.chat.ImChat;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatLastMessage;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.support.utils.FunctionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Mapper
public interface ImChatAppTransformer {
    ImChatAppTransformer INSTANCE = Mappers.getMapper(ImChatAppTransformer.class);

    default List<ImChatItemDTO> imChatListFrom(List<ImChat> chatList, List<ImChatLastMessage> chatLastMessageList) {
        Map<ImChatId, ImChatLastMessage> chatLastMessageMap = FunctionUtils.mappingMap(chatLastMessageList, ImChatLastMessage::getChatId, Function.identity());
        return FunctionUtils.mappingList(chatList, chat -> INSTANCE.imChatItemDtoFrom(chat, chatLastMessageMap.get(chat.getId())));
    }

    @Mappings(value = {
            @Mapping(target = "chatId", source = "chat.id.value"),
            @Mapping(target = "chatName", source = "chat.name.value"),
            @Mapping(target = "chatType", source = "chat.type"),
            @Mapping(target = "chatLastMessageType", source = "chatLastMessage.message.content.type"),
            @Mapping(target = "chatLastMessageContent", source = "chatLastMessage.message.content.value"),
    })
    ImChatItemDTO imChatItemDtoFrom(ImChat chat, ImChatLastMessage chatLastMessage);
}
