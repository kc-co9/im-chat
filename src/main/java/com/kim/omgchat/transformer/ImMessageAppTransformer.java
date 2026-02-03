package com.kim.omgchat.transformer;

import com.kim.omgchat.domain.message.ImPrivateMessage;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageNotifyDTO;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageReadNotifyDTO;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageRevokeNotifyDTO;
import com.kim.omgchat.model.io.im.ImPrivateMessageReadRequest;
import com.kim.omgchat.model.io.im.ImPrivateMessageRevokeRequest;
import com.kim.omgchat.model.io.im.ImPrivateMessageSendRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ImMessageAppTransformer {
    ImMessageAppTransformer INSTANCE = Mappers.getMapper(ImMessageAppTransformer.class);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "senderId", source = "senderId.value"),
            @Mapping(target = "receiverId", source = "receiverId.value"),
            @Mapping(target = "messageType", source = "type"),
            @Mapping(target = "sendTime", source = "sendTime")}
    )
    ImPrivateMessageNotifyDTO imPrivateMessageNotifyDtoFrom(ImPrivateMessage imMessage);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "receiverId", source = "receiverId.value"),
            @Mapping(target = "chatId", source = "chatId.value")}
    )
    ImPrivateMessageRevokeNotifyDTO imPrivateMessageRevokeNotifyDtoFrom(ImPrivateMessage imMessage);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "receiverId", source = "senderId.value"),
            @Mapping(target = "chatId", source = "chatId.value")}
    )
    ImPrivateMessageReadNotifyDTO imPrivateMessageReadNotifyDtoFrom(ImPrivateMessage imMessage);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "request.chatId"),
            @Mapping(target = "senderId", source = "userId"),
            @Mapping(target = "receiverId", source = "request.receiverId"),
            @Mapping(target = "messageToken", source = "request.messageToken"),
            @Mapping(target = "messageType", source = "request.messageType"),
            @Mapping(target = "messageContent", source = "request.messageContent")
    })
    ImPrivateMessageSendCmd imPrivateMessageSendCmdFrom(Long userId, ImPrivateMessageSendRequest request);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "request.chatId"),
            @Mapping(target = "userId", source = "userId"),
            @Mapping(target = "messageId", source = "request.messageId"),
    })
    ImPrivateMessageReadCmd imPrivateMessageReadCmdFrom(Long userId, ImPrivateMessageReadRequest request);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "request.chatId"),
            @Mapping(target = "userId", source = "userId"),
            @Mapping(target = "messageId", source = "request.messageId"),
    })
    ImPrivateMessageRevokeCmd imPrivateMessageReadCmdFrom(Long userId, ImPrivateMessageRevokeRequest request);
}
