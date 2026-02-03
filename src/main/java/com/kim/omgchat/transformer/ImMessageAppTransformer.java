package com.kim.omgchat.transformer;

import com.kim.omgchat.domain.message.ImMessageReadEvent;
import com.kim.omgchat.domain.message.ImMessageRevokedEvent;
import com.kim.omgchat.domain.message.ImMessageSentEvent;
import com.kim.omgchat.domain.message.ImMessageType;
import com.kim.omgchat.domain.message.ImPrivateMessage;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateReadNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateRevokedNotifyCmd;
import com.kim.omgchat.model.enums.ImMessageTypeEnum;
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
            @Mapping(target = "receiverId", source = "receiverId.value"),
            @Mapping(target = "chatId", source = "chatId.value")}
    )
    ImPrivateRevokedNotifyCmd imPrivateMessageRevokeNotifyDtoFrom(ImPrivateMessage imMessage);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "receiverId", source = "senderId.value"),
            @Mapping(target = "chatId", source = "chatId.value")}
    )
    ImPrivateReadNotifyCmd imPrivateMessageReadNotifyDtoFrom(ImPrivateMessage imMessage);

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


    @Mappings(value = {
            @Mapping(target = "messageId", source = "messageId"),
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "senderId", source = "senderId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageType", source = "messageType"),
            @Mapping(target = "sendTime", source = "sendTime")})
    ImPrivateSentNotifyCmd imPrivateSentNotifyCmdFrom(ImMessageSentEvent event);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageId", source = "messageId")})
    ImPrivateRevokedNotifyCmd imPrivateRevokedNotifyCmdFrom(ImMessageRevokedEvent event);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageId", source = "messageId")})
    ImPrivateReadNotifyCmd imPrivateMessageReadNotifyCmdFrom(ImMessageReadEvent event);

    ImMessageTypeEnum imMessageTypeEnumFrom(ImMessageType type);
}
