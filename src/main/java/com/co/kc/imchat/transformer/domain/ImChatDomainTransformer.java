package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.domain.chat.ImChat;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ImChatDomainTransformer {
    ImChatDomainTransformer INSTANCE = Mappers.getMapper(ImChatDomainTransformer.class);

    List<ImChat> imChatListFrom(List<DbImChat> dbImChatList);

    @Mappings(value = {
            @Mapping(target = "incrId", source = "id"),
            @Mapping(target = "id.value", source = "chatId"),
            @Mapping(target = "name.value", source = "name"),
            @Mapping(target = "type", source = "type")
    })
    ImChat imChatFrom(DbImChat dbImChat);

    @Mappings(value = {
            @Mapping(target = "incrId", source = "dbImChat.id"),
            @Mapping(target = "id.value", source = "dbImChat.chatId"),
            @Mapping(target = "name.value", source = "dbImChat.name"),
            @Mapping(target = "type", source = "dbImChat.type"),
            @Mapping(target = "pair.member1.value", source = "dbImPrivateChat.member1"),
            @Mapping(target = "pair.member2.value", source = "dbImPrivateChat.member2")
    })
    ImPrivateChat imPrivateChatFrom(DbImChat dbImChat, DbImPrivateChat dbImPrivateChat);

    @Mappings(value = {
            @Mapping(target = "incrId", source = "dbImChat.id"),
            @Mapping(target = "id.value", source = "dbImChat.chatId"),
            @Mapping(target = "name.value", source = "dbImChat.name"),
            @Mapping(target = "type", source = "dbImChat.type"),
            @Mapping(target = "ownerId.value", source = "dbImGroupChat.ownerId"),
            @Mapping(target = "members", source = "dbImGroupMemberList"),
    })
    ImGroupChat imGroupChatFrom(DbImChat dbImChat, DbImGroupChat dbImGroupChat, List<DbImGroupMember> dbImGroupMemberList);

    List<ImGroupMember> imGroupMemberListFrom(List<DbImGroupMember> dbImGroupMemberList);

    @Mappings(value = {
            @Mapping(target = "userId.value", source = "userId"),
            @Mapping(target = "groupAlias.value", source = "groupAlias"),
            @Mapping(target = "userAlias.value", source = "userAlias"),
    })
    ImGroupMember imGroupMemberFrom(DbImGroupMember dbImGroupMember);

    @ValueMappings(value = {
            @ValueMapping(source = "NONE", target = "PRIVATE"),
            @ValueMapping(source = "PRIVATE", target = "PRIVATE"),
            @ValueMapping(source = "GROUP", target = "GROUP")
    })
    ImChatType imChatTypeFrom(DbImChatType type);

}