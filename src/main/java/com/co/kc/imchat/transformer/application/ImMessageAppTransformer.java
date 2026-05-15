package com.co.kc.imchat.transformer.application;

import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImGroupMessageSentEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageSentEvent;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessage;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageReceiveCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageReadCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageSendCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageReceiveCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupRevokedNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupSentNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateRevokedNotifyCmd;
import com.co.kc.imchat.model.cqrs.dto.group.GroupMessageDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import com.co.kc.imchat.model.io.group.GroupMessageReceiveRequest;
import com.co.kc.imchat.model.io.group.GroupMessageReadRequest;
import com.co.kc.imchat.model.io.group.GroupMessageRevokeRequest;
import com.co.kc.imchat.model.io.group.GroupMessageSendRequest;
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

    List<ImPrivateMessageDTO> imPrivateMessageDtoListFrom(List<ImPrivateInboxMessage> messageList);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "token", source = "token.value"),
            @Mapping(target = "type", source = "content.type"),
            @Mapping(target = "content", source = "content.value"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "senderId", source = "senderId.value"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "readTime", source = "readTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")}
    )
    ImPrivateMessageDTO imPrivateMessageDtoFrom(ImPrivateInboxMessage message);

    List<GroupMessageDTO> groupMessageDtoListFrom(List<ImGroupInboxMessage> messageList);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "token", source = "token.value"),
            @Mapping(target = "type", source = "content.type"),
            @Mapping(target = "content", source = "content.value"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "senderId", source = "senderId.value"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "readTime", source = "readTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")}
    )
    GroupMessageDTO groupMessageDtoFrom(ImGroupInboxMessage message);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "request.chatId"),
            @Mapping(target = "userId", source = "userId"),
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
    GroupMessageSendCmd groupMessageSendCmdFrom(Long userId, GroupMessageSendRequest request);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "request.chatId"),
            @Mapping(target = "userId", source = "userId"),
            @Mapping(target = "messageId", source = "request.messageId"),
    })
    GroupMessageReceiveCmd groupMessageReceiveCmdFrom(Long userId, GroupMessageReceiveRequest request);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "request.chatId"),
            @Mapping(target = "userId", source = "userId"),
            @Mapping(target = "messageId", source = "request.messageId"),
    })
    GroupMessageReadCmd groupMessageReadCmdFrom(Long userId, GroupMessageReadRequest request);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "request.chatId"),
            @Mapping(target = "userId", source = "userId"),
            @Mapping(target = "messageId", source = "request.messageId"),
    })
    GroupMessageRevokeCmd groupMessageRevokeCmdFrom(Long userId, GroupMessageRevokeRequest request);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "messageId"),
            @Mapping(target = "chatId", source = "receiverChatId"),
            @Mapping(target = "senderId", source = "senderId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageType", source = "messageType"),
            @Mapping(target = "sendTime", source = "sendTime")})
    ImPrivateSentNotifyCmd imPrivateSentNotifyCmdFrom(ImPrivateMessageSentEvent event);

    @Mappings(value = {
            @Mapping(target = "receiverChatId", source = "chatId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageId", source = "messageId")})
    ImPrivateRevokedNotifyCmd imPrivateRevokedNotifyCmdFrom(ImPrivateMessageRevokedEvent event);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "event.messageId"),
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "senderId", source = "event.senderId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageType", source = "event.messageType"),
            @Mapping(target = "sendTime", source = "event.sendTime")})
    GroupSentNotifyCmd groupSentNotifyCmdFrom(Long receiverId, Long chatId, ImGroupMessageSentEvent event);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageId", source = "event.messageId")})
    GroupRevokedNotifyCmd groupRevokedNotifyCmdFrom(Long receiverId, Long chatId, ImGroupMessageRevokedEvent event);

    ImMessageTypeEnum imMessageTypeEnumFrom(ImMessageType type);

}
