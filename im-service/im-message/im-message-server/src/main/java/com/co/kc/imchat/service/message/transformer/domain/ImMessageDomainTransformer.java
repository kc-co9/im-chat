package com.co.kc.imchat.service.message.transformer.domain;

import com.co.kc.imchat.service.message.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateMessageStatus;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatId;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageContent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupInboxMessage;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImPrivateInboxMessage;
import com.co.kc.imchat.service.message.infrastructure.mybatis.enums.DbGroupImMessageStatus;
import com.co.kc.imchat.service.message.infrastructure.mybatis.enums.DbPrivateImMessageStatus;
import com.co.kc.imchat.service.message.infrastructure.mybatis.enums.DbImMessageType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Mappings;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ImMessageDomainTransformer {
    ImMessageDomainTransformer INSTANCE = Mappers.getMapper(ImMessageDomainTransformer.class);

    @Mappings(value = {
            @Mapping(target = "pkId", source = "id"),
            @Mapping(target = "id.value", source = "messageId"),
            @Mapping(target = "chatId.value", source = "chatId"),
            @Mapping(target = "userId.value", source = "userId"),
            @Mapping(target = "token.value", source = "token"),
            @Mapping(target = "senderId.value", source = "senderId"),
            @Mapping(target = "content.type", source = "type"),
            @Mapping(target = "content.value", source = "content"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "receivedTime", source = "receiveTime"),
            @Mapping(target = "readTime", source = "readTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")
    })
    ImPrivateInboxMessage imPrivateInboxMessageFrom(DbImPrivateInboxMessage db);

    List<ImGroupInboxMessage> imGroupInboxMessageListFrom(List<DbImGroupInboxMessage> records);

    default ImGroupInboxMessage imGroupInboxMessageFrom(DbImGroupInboxMessage db) {
        return ImGroupInboxMessage.builder()
                .pkId(db.getId())
                .id(new ImMessageId(db.getMessageId()))
                .groupId(new GroupId(db.getGroupId()))
                .chatId(new ImChatId(db.getChatId()))
                .userId(new UserId(db.getUserId()))
                .token(new ImMessageToken(db.getToken()))
                .senderId(new UserId(db.getSenderId()))
                .content(new ImMessageContent(imMessageTypeFrom(db.getType()), db.getContent()))
                .status(imGroupMessageStatusFrom(db.getStatus()))
                .sendTime(db.getSendTime())
                .receivedTime(db.getReceiveTime())
                .readTime(db.getReadTime())
                .revokeTime(db.getRevokeTime())
                .build();
    }

    @ValueMappings(value = {
            @ValueMapping(target = MappingConstants.NULL, source = "NONE"),
            @ValueMapping(target = "TEXT", source = "TEXT"),
            @ValueMapping(target = "IMAGE", source = "IMAGE"),
            @ValueMapping(target = "AUDIO", source = "AUDIO"),
            @ValueMapping(target = "VIDEO", source = "VIDEO"),
            @ValueMapping(target = "FILE", source = "FILE"),
            @ValueMapping(target = "STICKER", source = "STICKER"),
            @ValueMapping(target = "SYSTEM", source = "SYSTEM"),
    })
    ImMessageType imMessageTypeFrom(DbImMessageType dbType);

    default ImPrivateMessageStatus imPrivateMessageStatusFrom(DbPrivateImMessageStatus dbStatus) {
        if (dbStatus == null || dbStatus == DbPrivateImMessageStatus.NONE) {
            return null;
        }
        switch (dbStatus) {
            case SENT:
                return ImPrivateMessageStatus.SENT;
            case RECEIVED:
                return ImPrivateMessageStatus.RECEIVED;
            case READ:
                return ImPrivateMessageStatus.READ;
            case REVOKED:
                return ImPrivateMessageStatus.REVOKED;
            default:
                return null;
        }
    }

    default ImGroupMessageStatus imGroupMessageStatusFrom(DbGroupImMessageStatus dbStatus) {
        if (dbStatus == null || dbStatus == DbGroupImMessageStatus.NONE) {
            return null;
        }
        switch (dbStatus) {
            case SENT:
                return ImGroupMessageStatus.SENT;
            case RECEIVED:
                return ImGroupMessageStatus.RECEIVED;
            case READ:
                return ImGroupMessageStatus.READ;
            case REVOKED:
                return ImGroupMessageStatus.REVOKED;
            default:
                return null;
        }
    }

}
