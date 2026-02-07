package com.co.kc.imchat.transformer.application;

import com.co.kc.imchat.domain.message.ImGroupMessage;
import com.co.kc.imchat.domain.message.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImGroupMessageSentEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageReadEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageSentEvent;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.message.ImPrivateMessage;
import com.co.kc.imchat.model.cqrs.command.im.ImGroupMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImGroupMessageSendCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageReceiveCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImGroupRevokedNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImGroupSentNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateReadNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateRevokedNotifyCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupMessageDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import com.co.kc.imchat.model.io.im.ImGroupMessageRevokeRequest;
import com.co.kc.imchat.model.io.im.ImGroupMessageSendRequest;
import com.co.kc.imchat.model.io.im.ImPrivateMessageReadRequest;
import com.co.kc.imchat.model.io.im.ImPrivateMessageReceiveRequest;
import com.co.kc.imchat.model.io.im.ImPrivateMessageRevokeRequest;
import com.co.kc.imchat.model.io.im.ImPrivateMessageSendRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ImMessageAppTransformer {
    ImMessageAppTransformer INSTANCE = Mappers.getMapper(ImMessageAppTransformer.class);

    List<ImPrivateMessageDTO> imPrivateMessageDtoListFrom(List<ImPrivateMessage> messageList);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "token", source = "token.value"),
            @Mapping(target = "type", source = "content.type"),
            @Mapping(target = "content", source = "content.value"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "senderId", source = "senderId.value"),
            @Mapping(target = "receiverId", source = "receiverId.value"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "readTime", source = "readTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")}
    )
    ImPrivateMessageDTO imPrivateMessageDtoFrom(ImPrivateMessage message);

    List<ImGroupMessageDTO> imGroupMessageDtoListFrom(List<ImGroupMessage> messageList);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "token", source = "token.value"),
            @Mapping(target = "type", source = "content.type"),
            @Mapping(target = "content", source = "content.value"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "senderId", source = "senderId.value"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")}
    )
    ImGroupMessageDTO imGroupMessageDtoFrom(ImGroupMessage message);


    @Mappings(value = {
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "receiverId", source = "senderId.value"),
            @Mapping(target = "chatId", source = "chatId.value")}
    )
    ImPrivateReadNotifyCmd imPrivateMessageReadNotifyDtoFrom(ImPrivateMessage imMessage);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "request.chatId"),
            @Mapping(target = "senderId", source = "userId"),
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
    ImPrivateMessageReceiveCmd imPrivateMessageReceiveCmdFrom(Long userId, ImPrivateMessageReceiveRequest request);

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
    ImPrivateMessageRevokeCmd imPrivateMessageRevokeCmdFrom(Long userId, ImPrivateMessageRevokeRequest request);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "request.chatId"),
            @Mapping(target = "senderId", source = "userId"),
            @Mapping(target = "messageToken", source = "request.messageToken"),
            @Mapping(target = "messageType", source = "request.messageType"),
            @Mapping(target = "messageContent", source = "request.messageContent")
    })
    ImGroupMessageSendCmd imGroupMessageSendCmdFrom(Long userId, ImGroupMessageSendRequest request);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "request.chatId"),
            @Mapping(target = "userId", source = "userId"),
            @Mapping(target = "messageId", source = "request.messageId"),
    })
    ImGroupMessageRevokeCmd imGroupMessageRevokeCmdFrom(Long userId, ImGroupMessageRevokeRequest request);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "messageId"),
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "senderId", source = "senderId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageType", source = "messageType"),
            @Mapping(target = "sendTime", source = "sendTime")})
    ImPrivateSentNotifyCmd imPrivateSentNotifyCmdFrom(ImPrivateMessageSentEvent event);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageId", source = "messageId")})
    ImPrivateRevokedNotifyCmd imPrivateRevokedNotifyCmdFrom(ImPrivateMessageRevokedEvent event);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageId", source = "messageId")})
    ImPrivateReadNotifyCmd imPrivateMessageReadNotifyCmdFrom(ImPrivateMessageReadEvent event);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "event.messageId"),
            @Mapping(target = "chatId", source = "event.chatId"),
            @Mapping(target = "senderId", source = "event.senderId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageType", source = "event.messageType"),
            @Mapping(target = "sendTime", source = "event.sendTime")})
    ImGroupSentNotifyCmd imGroupSentNotifyCmdFrom(Long receiverId, ImGroupMessageSentEvent event);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "event.chatId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageId", source = "event.messageId")})
    ImGroupRevokedNotifyCmd imGroupRevokedNotifyCmdFrom(Long receiverId, ImGroupMessageRevokedEvent event);

    ImMessageTypeEnum imMessageTypeEnumFrom(ImMessageType type);

}
