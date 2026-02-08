package com.co.kc.imchat.transformer.http;

import com.co.kc.imchat.model.cqrs.dto.im.ImGroupMessageDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.model.io.im.ImGroupMessageDetailResponse;
import com.co.kc.imchat.model.io.im.ImGroupMessageHistoryQueryResponse;
import com.co.kc.imchat.model.io.im.ImPrivateMessageHistoryQueryResponse;
import com.co.kc.imchat.model.io.im.ImPrivateMessageDetailQueryResponse;
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

    List<ImGroupMessageHistoryQueryResponse.MessageItem> imGroupMessageItemListFrom(List<ImGroupMessageDTO> messageList);

    @Mappings(value = {
            @Mapping(target = "messageId", source = "messageId"),
            @Mapping(target = "token", source = "token"),
            @Mapping(target = "type", source = "type"),
            @Mapping(target = "content", source = "content"),
            @Mapping(target = "chatId", source = "chatId"),
            @Mapping(target = "senderId", source = "senderId"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")}
    )
    ImGroupMessageHistoryQueryResponse.MessageItem imGroupMessageItemFrom(ImGroupMessageDTO message);

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
            @Mapping(target = "revokeTime", source = "revokeTime")}
    )
    ImGroupMessageDetailResponse imGroupMessageDetailResponseFrom(ImGroupMessageDTO imGroupMessageDTO);
}
