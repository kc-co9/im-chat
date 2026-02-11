package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.domain.message.ImGroupMessage;
import com.co.kc.imchat.domain.message.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.ImPrivateMessageStatus;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.message.ImPrivateMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateMessage;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbGroupImMessageStatus;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbPrivateImMessageStatus;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImMessageType;
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

    List<ImPrivateMessage> imPrivateMessageListFrom(List<DbImPrivateMessage> dbMessageList);

    @Mappings(value = {
            @Mapping(target = "incrId", source = "id"),
            @Mapping(target = "id.value", source = "messageId"),
            @Mapping(target = "chatId.value", source = "chatId"),
            @Mapping(target = "token.value", source = "token"),
            @Mapping(target = "senderId.value", source = "senderId"),
            @Mapping(target = "content.value", source = "content"),
            @Mapping(target = "status", source = "status"),
            @Mapping(target = "sendTime", source = "sendTime"),
            @Mapping(target = "receivedTime", source = "receiveTime"),
            @Mapping(target = "revokeTime", source = "revokeTime"),
            @Mapping(target = "receiverId.value", source = "receiverId")
    })
    ImPrivateMessage imPrivateMessageFrom(DbImPrivateMessage dbMessage);

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

    @ValueMappings(value = {
            @ValueMapping(target = MappingConstants.NULL, source = "NONE"),
            @ValueMapping(target = "SENT", source = "SENT"),
            @ValueMapping(target = "RECEIVED", source = "RECEIVED"),
            @ValueMapping(target = "READ", source = "READ"),
            @ValueMapping(target = "REVOKED", source = "REVOKED")
    })
    ImPrivateMessageStatus imPrivateMessageStatusFrom(DbPrivateImMessageStatus dbStatus);

    @ValueMappings(value = {
            @ValueMapping(target = MappingConstants.NULL, source = "NONE"),
            @ValueMapping(target = "SENT", source = "SENT"),
            @ValueMapping(target = "REVOKED", source = "REVOKED")
    })
    ImGroupMessageStatus imGroupMessageStatusFrom(DbGroupImMessageStatus dbStatus);

}