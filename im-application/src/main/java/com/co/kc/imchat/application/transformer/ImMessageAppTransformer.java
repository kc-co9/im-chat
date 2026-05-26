package com.co.kc.imchat.application.transformer;

import com.co.kc.imchat.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.event.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.domain.message.event.ImGroupMessageSentEvent;
import com.co.kc.imchat.domain.message.event.ImPrivateMessageRevokedEvent;
import com.co.kc.imchat.domain.message.event.ImPrivateMessageSentEvent;
import com.co.kc.imchat.domain.message.model.ImMessageType;
import com.co.kc.imchat.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.application.model.notification.ImGroupRevokedNotification;
import com.co.kc.imchat.application.model.notification.ImGroupSentNotification;
import com.co.kc.imchat.application.model.notification.ImPrivateSentNotification;
import com.co.kc.imchat.application.model.notification.ImPrivateRevokedNotification;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupMessageDTO;
import com.co.kc.imchat.application.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;
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
            @Mapping(target = "content", source = "visibleContent"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "senderId", source = "senderId.value"),
            @Mapping(target = "receiverId", source = "userId.value"),
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
            @Mapping(target = "content", source = "visibleContent"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "senderId", source = "senderId.value"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "readTime", source = "readTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")}
    )
    GroupMessageDTO groupMessageDtoFrom(ImGroupInboxMessage message);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "messageId"),
            @Mapping(target = "chatId", source = "receiverChatId"),
            @Mapping(target = "senderId", source = "senderId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageType", source = "messageType"),
            @Mapping(target = "sendTime", source = "sendTime")})
    ImPrivateSentNotification imPrivateSentNotificationFrom(ImPrivateMessageSentEvent event);

    @Mappings(value = {
            @Mapping(target = "receiverChatId", source = "chatId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageId", source = "messageId")})
    ImPrivateRevokedNotification imPrivateRevokedNotificationFrom(ImPrivateMessageRevokedEvent event);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "event.messageId"),
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "senderId", source = "event.senderId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageType", source = "event.messageType"),
            @Mapping(target = "sendTime", source = "event.sendTime")})
    ImGroupSentNotification imGroupSentNotificationFrom(Long receiverId, Long chatId, ImGroupMessageSentEvent event);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "receiverId", source = "receiverId"),
            @Mapping(target = "messageId", source = "event.messageId")})
    ImGroupRevokedNotification imGroupRevokedNotificationFrom(Long receiverId, Long chatId, ImGroupMessageRevokedEvent event);

    ImMessageTypeEnum imMessageTypeEnumFrom(ImMessageType type);

}
