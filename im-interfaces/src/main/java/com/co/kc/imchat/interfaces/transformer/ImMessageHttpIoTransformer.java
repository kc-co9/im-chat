package com.co.kc.imchat.interfaces.transformer;

import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageReceiveCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageReadCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageRevokeCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageSendCmd;
import com.co.kc.imchat.application.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.application.model.cqrs.command.im.ImPrivateMessageReceiveCmd;
import com.co.kc.imchat.application.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.application.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupMessageDTO;
import com.co.kc.imchat.application.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.interfaces.model.io.group.GroupMessageDetailResponse;
import com.co.kc.imchat.interfaces.model.io.group.GroupMessageHistoryQueryResponse;
import com.co.kc.imchat.interfaces.model.io.group.GroupMessageReceiveRequest;
import com.co.kc.imchat.interfaces.model.io.group.GroupMessageReadRequest;
import com.co.kc.imchat.interfaces.model.io.group.GroupMessageRevokeRequest;
import com.co.kc.imchat.interfaces.model.io.group.GroupMessageSendRequest;
import com.co.kc.imchat.interfaces.model.io.im.ImPrivateMessageDetailQueryResponse;
import com.co.kc.imchat.interfaces.model.io.im.ImPrivateMessageHistoryQueryResponse;
import com.co.kc.imchat.interfaces.model.io.im.ImPrivateMessageReadRequest;
import com.co.kc.imchat.interfaces.model.io.im.ImPrivateMessageReceiveRequest;
import com.co.kc.imchat.interfaces.model.io.im.ImPrivateMessageRevokeRequest;
import com.co.kc.imchat.interfaces.model.io.im.ImPrivateMessageSendRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ImMessageHttpIoTransformer {
    ImMessageHttpIoTransformer INSTANCE = Mappers.getMapper(ImMessageHttpIoTransformer.class);


    List<ImPrivateMessageHistoryQueryResponse.MessageItem> imPrivateMessageItemListFrom(List<ImPrivateMessageDTO> messageList);

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
    ImPrivateMessageHistoryQueryResponse.MessageItem imPrivateMessageItemFrom(ImPrivateMessageDTO message);

    List<GroupMessageHistoryQueryResponse.MessageItem> groupMessageItemListFrom(List<GroupMessageDTO> messageList);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "messageId"),
            @Mapping(target = "token", source = "token"),
            @Mapping(target = "type", source = "type"),
            @Mapping(target = "content", source = "content"),
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "senderId", source = "senderId"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "readTime", source = "readTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")}
    )
    GroupMessageHistoryQueryResponse.MessageItem groupMessageItemFrom(GroupMessageDTO message);

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
    ImPrivateMessageDetailQueryResponse imPrivateMessageDetailQueryResponseFrom(ImPrivateMessageDTO imPrivateMessageDTO);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "messageId"),
            @Mapping(target = "token", source = "token"),
            @Mapping(target = "type", source = "type"),
            @Mapping(target = "content", source = "content"),
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "senderId", source = "senderId"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "readTime", source = "readTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")}
    )
    GroupMessageDetailResponse groupMessageDetailResponseFrom(GroupMessageDTO imGroupMessageDTO);

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
}
