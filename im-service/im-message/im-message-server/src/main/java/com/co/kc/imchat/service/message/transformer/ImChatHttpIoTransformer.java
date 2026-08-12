package com.co.kc.imchat.service.message.transformer;

import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.service.message.model.cqrs.dto.group.GroupChatOpenDTO;
import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImPrivateChatOpenDTO;
import com.co.kc.imchat.service.message.model.io.chat.ImChatListResponse;
import com.co.kc.imchat.service.message.model.io.group.GroupChatOpenResponse;
import com.co.kc.imchat.service.message.model.io.chat.ImPrivateChatOpenResponse;
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
            @Mapping(target = "chatType", source = "chatType"),
            @Mapping(target = "lastMessageType", source = "lastMessageType"),
            @Mapping(target = "lastMessageContent", source = "lastMessageContent"),
            @Mapping(target = "lastMessageTime", source = "lastMessageTime")
    })
    ImChatListResponse.ImChatItem imChatItemFrom(ImChatItemDTO imChatItem);

    ImPrivateChatOpenResponse imPrivateChatOpenResponseFrom(ImPrivateChatOpenDTO dto);

    GroupChatOpenResponse groupChatOpenResponseFrom(GroupChatOpenDTO dto);
}
