package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.domain.message.ImGroupMessage;
import com.co.kc.imchat.domain.message.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.ImPrivateMessageStatus;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateInboxMessage;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbGroupImMessageStatus;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbPrivateImMessageStatus;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImMessageType;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
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

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mappings(value = {
            @Mapping(target = "incrId", source = "id"),
            @Mapping(target = "id.value", source = "messageId"),
            @Mapping(target = "chatId.value", source = "chatId"),
            @Mapping(target = "userId.value", source = "userId"),
            @Mapping(target = "token.value", source = "token"),
            @Mapping(target = "senderId.value", source = "senderId"),
            @Mapping(target = "content.value", source = "content"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "receivedTime", source = "receiveTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")
    })
    ImPrivateInboxMessage imPrivateInboxMessageFrom(DbImPrivateInboxMessage dbMessage);

    List<ImGroupMessage> imGroupMessageListFrom(List<DbImGroupMessage> records);

    @Mappings(value = {
            @Mapping(target = "id.value", source = "messageId"),
            @Mapping(target = "chatId.value", source = "chatId"),
            @Mapping(target = "token.value", source = "token"),
            @Mapping(target = "senderId.value", source = "senderId"),
            @Mapping(target = "content.value", source = "content"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "revokeTime", source = "revokeTime"),
    })
    ImGroupMessage imGroupMessageFrom(DbImGroupMessage dbImGroupMessage);

    @ValueMappings(value = {
            @ValueMapping(target = MappingConstants.NULL, source = "NONE"),
            @ValueMapping(target = "TEXT", source = "TEXT"),
            @ValueMapping(target = "IMAGE", source = "IMAGE"),
            @ValueMapping(target = "AUDIO", source = "AUDIO"),
            @ValueMapping(target = "VIDEO", source = "VIDEO"),
            @ValueMapping(target = "FILE", source = "FILE"),
            @ValueMapping(target = "STICKER", source = "STICKER"),
    })
    ImMessageType imMessageTypeFrom(DbImMessageType dbType);

    default ImPrivateMessageStatus imPrivateMessageStatusFrom(DbPrivateImMessageStatus dbStatus) {
        if (dbStatus == null || dbStatus == DbPrivateImMessageStatus.NONE) {
            return null;
        }
        switch (dbStatus) {
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
            case REVOKED:
                return ImGroupMessageStatus.REVOKED;
            default:
                return null;
        }
    }

}