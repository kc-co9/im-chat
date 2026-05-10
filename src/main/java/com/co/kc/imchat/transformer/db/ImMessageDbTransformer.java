package com.co.kc.imchat.transformer.db;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Mappings;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

import com.co.kc.imchat.domain.message.ImGroupMessage;
import com.co.kc.imchat.domain.message.ImPrivateMessageStatus;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMessage;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbPrivateImMessageStatus;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImMessageType;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ImMessageDbTransformer {
    ImMessageDbTransformer INSTANCE = Mappers.getMapper(ImMessageDbTransformer.class);

    @Mappings(value = {
            @Mapping(target = "id", source = "incrId"),
            @Mapping(target = "messageId", source = "id.value"),
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "token", source = "token.value"),
            @Mapping(target = "senderId", source = "senderId.value"),
            @Mapping(target = "content", source = "content.value"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "revokeTime", source = "revokeTime")
    })
    DbImGroupMessage dbImGroupMessageFrom(ImGroupMessage groupMessage);

    @ValueMappings(value = {
            @ValueMapping(target = "NONE", source = MappingConstants.NULL),
            @ValueMapping(target = "TEXT", source = "TEXT"),
            @ValueMapping(target = "IMAGE", source = "IMAGE"),
            @ValueMapping(target = "AUDIO", source = "AUDIO"),
            @ValueMapping(target = "VIDEO", source = "VIDEO"),
            @ValueMapping(target = "FILE", source = "FILE")
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
}