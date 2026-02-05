package com.co.kc.imchat.transformer.db;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

import com.co.kc.imchat.domain.chat.ImChat;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImChat;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ImChatDbTransformer {
    ImChatDbTransformer INSTANCE = Mappers.getMapper(ImChatDbTransformer.class);

    @Mappings(value = {
            @Mapping(target = "id", source = "incrId"),
            @Mapping(target = "chatId", source = "id.value"),
            @Mapping(target = "name", source = "name.value"),
            @Mapping(target = "type", source = "type")
    })
    DbImChat dbImChatFrom(ImChat imChat);


    @ValueMappings(value = {
            @ValueMapping(source = "PRIVATE", target = "PRIVATE"),
            @ValueMapping(source = "GROUP", target = "GROUP")
    })
    DbImChatType dbImChatTypeFrom(ImChatType type);
}
