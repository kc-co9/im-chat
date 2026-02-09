package com.co.kc.imchat.transformer.db;

import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.support.utils.JsonUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface ImChatDbTransformer {
    ImChatDbTransformer INSTANCE = Mappers.getMapper(ImChatDbTransformer.class);

    @Mappings(value = {
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "chatId", source = "id.value"),
            @Mapping(target = "member1", source = "pair.member1.value"),
            @Mapping(target = "member2", source = "pair.member2.value")
    })
    DbImPrivateChat dbImPrivateChatFrom(ImPrivateChat imPrivateChat);

    default DbImGroupChat dbImGroupChatFrom(ImGroupChat imGroupChat) {
        DbImGroupChat dbImGroupChat = new DbImGroupChat();
        dbImGroupChat.setChatId(imGroupChat.getId().getValue());
        dbImGroupChat.setOwnerId(imGroupChat.getOwnerId().getValue());
        dbImGroupChat.setNotification(imGroupChat.getNotification().getValue());
        dbImGroupChat.setSetting(JsonUtils.toJson(imGroupChat.getSetting()));
        return dbImGroupChat;
    }

    List<DbImGroupMember> dbImGroupMemberListFrom(List<ImGroupMember> imGroupMembers);

    default DbImGroupMember dbImGroupMemberFrom(ImGroupMember imGroupMember) {
        DbImGroupMember dbImGroupMember = new DbImGroupMember();
        dbImGroupMember.setChatId(imGroupMember.getChatId().getValue());
        dbImGroupMember.setUserId(imGroupMember.getUserId().getValue());
        dbImGroupMember.setUserAlias(imGroupMember.getUserAlias().getValue());
        dbImGroupMember.setGroupAlias(imGroupMember.getGroupAlias().getValue());
        dbImGroupMember.setSetting(JsonUtils.toJson(imGroupMember.getSetting()));
        return dbImGroupMember;
    }

    @ValueMappings(value = {
            @ValueMapping(source = "PRIVATE", target = "PRIVATE"),
            @ValueMapping(source = "GROUP", target = "GROUP")
    })
    DbImChatType dbImChatTypeFrom(ImChatType type);

}
