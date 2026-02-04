package com.kim.omgchat.transformer;

import com.kim.omgchat.model.cqrs.dto.im.ImMessageDTO;
import com.kim.omgchat.model.io.im.ImPrivateMessageQueryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ImMessageHttpIoTransformer {
    ImMessageHttpIoTransformer INSTANCE = Mappers.getMapper(ImMessageHttpIoTransformer.class);


    List<ImPrivateMessageQueryResponse.MessageItem> imPrivateMessageItemListFrom(List<ImMessageDTO> messageList);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "messageId"),
            @Mapping(target = "token", source = "token"),
            @Mapping(target = "type", source = "type"),
            @Mapping(target = "content", source = "content"),
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "senderId", source = "senderId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "readTime", source = "readTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")}
    )
    ImPrivateMessageQueryResponse.MessageItem imPrivateMessageItemFrom(ImMessageDTO message);

}
