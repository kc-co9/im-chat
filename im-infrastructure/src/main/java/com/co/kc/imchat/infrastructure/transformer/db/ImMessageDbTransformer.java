package com.co.kc.imchat.infrastructure.transformer.db;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Mappings;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

import com.co.kc.imchat.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.model.ImMessageType;
import com.co.kc.imchat.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.message.model.ImPrivateMessageStatus;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupInboxMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateInboxMessage;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbGroupImMessageStatus;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbPrivateImMessageStatus;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImMessageType;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ImMessageDbTransformer {
    ImMessageDbTransformer INSTANCE = Mappers.getMapper(ImMessageDbTransformer.class);

    @Mappings(value = {
            @Mapping(target = "id", source = "pkId"),
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "groupId", source = "groupId.value"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "userId", source = "userId.value"),
            @Mapping(target = "token", source = "token.value"),
            @Mapping(target = "senderId", source = "senderId.value"),
            @Mapping(target = "type", source = "content.type"),
            @Mapping(target = "content", source = "content.value"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "receiveTime", source = "receivedTime"),
            @Mapping(target = "readTime", source = "readTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")
    })
    DbImGroupInboxMessage dbImGroupInboxMessageFrom(ImGroupInboxMessage message);

    @ValueMappings(value = {
            @ValueMapping(target = "NONE", source = MappingConstants.NULL),
            @ValueMapping(target = "TEXT", source = "TEXT"),
            @ValueMapping(target = "IMAGE", source = "IMAGE"),
            @ValueMapping(target = "AUDIO", source = "AUDIO"),
            @ValueMapping(target = "VIDEO", source = "VIDEO"),
            @ValueMapping(target = "FILE", source = "FILE"),
            @ValueMapping(target = "STICKER", source = "STICKER"),
            @ValueMapping(target = "SYSTEM", source = "SYSTEM")
    })
    DbImMessageType dbImMessageTypeFrom(ImMessageType type);

    default DbPrivateImMessageStatus dbImMessageStatusFrom(ImPrivateMessageStatus status) {
        if (status == null) {
            return DbPrivateImMessageStatus.NONE;
        }
        switch (status) {
            case SENT:
            case RECEIVED:
                return DbPrivateImMessageStatus.RECEIVED;
            case READ:
                return DbPrivateImMessageStatus.READ;
            case REVOKED:
                return DbPrivateImMessageStatus.REVOKED;
            default:
                return DbPrivateImMessageStatus.NONE;
        }
    }

    default DbGroupImMessageStatus dbImMessageStatusFrom(ImGroupMessageStatus status) {
        if (status == null) {
            return DbGroupImMessageStatus.NONE;
        }
        switch (status) {
            case SENT:
                return DbGroupImMessageStatus.SENT;
            case RECEIVED:
                return DbGroupImMessageStatus.RECEIVED;
            case READ:
                return DbGroupImMessageStatus.READ;
            case REVOKED:
                return DbGroupImMessageStatus.REVOKED;
            default:
                return DbGroupImMessageStatus.NONE;
        }
    }

    @Mappings(value = {
            @Mapping(target = "id", source = "pkId"),
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "userId", source = "userId.value"),
            @Mapping(target = "token", source = "token.value"),
            @Mapping(target = "senderId", source = "senderId.value"),
            @Mapping(target = "type", source = "content.type"),
            @Mapping(target = "content", source = "content.value"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "receiveTime", source = "receivedTime"),
            @Mapping(target = "readTime", source = "readTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")
    })
    DbImPrivateInboxMessage dbImPrivateInboxMessageFrom(ImPrivateInboxMessage message);
}
