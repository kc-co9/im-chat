package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.domain.chat.ImChat;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatLastMessage;
import com.co.kc.imchat.domain.chat.ImChatName;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupAlias;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.chat.ImGroupMemberSetting;
import com.co.kc.imchat.domain.chat.ImGroupNotification;
import com.co.kc.imchat.domain.chat.ImGroupSetting;
import com.co.kc.imchat.domain.chat.ImGroupUserAlias;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivatePair;
import com.co.kc.imchat.domain.message.ImPrivateMessageStatus;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImChatLastMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateMessage;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbPrivateImMessageStatus;
import com.co.kc.imchat.support.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Mappings;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(
        uses = {
                ImMessageDomainTransformer.class
        }
)
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

    default ImPrivateChat imPrivateChatFrom(DbImChat dbImChat, DbImPrivateChat dbImPrivateChat) {
        ImPrivateChat imPrivateChat = new ImPrivateChat();
        imPrivateChat.setPair(new ImPrivatePair(new UserId(dbImPrivateChat.getMember1()), new UserId(dbImPrivateChat.getMember2())));
        imPrivateChat.setId(new ImChatId(dbImChat.getChatId()));
        imPrivateChat.setName(new ImChatName(dbImChat.getName()));
        imPrivateChat.setType(INSTANCE.imChatTypeFrom(dbImChat.getType()));
        imPrivateChat.setIncrId(dbImChat.getId());
        return imPrivateChat;
    }

    default ImGroupChat imGroupChatFrom(DbImChat dbImChat, DbImGroupChat dbImGroupChat, List<DbImGroupMember> dbImGroupMemberList) {
        ImGroupChat imGroupChat = new ImGroupChat();
        imGroupChat.setOwnerId(new UserId(dbImGroupChat.getOwnerId()));
        imGroupChat.setMembers(INSTANCE.imGroupMemberListFrom(dbImGroupMemberList));
        imGroupChat.setNotification(new ImGroupNotification(dbImGroupChat.getNotification()));
        imGroupChat.setSetting(JsonUtils.fromJson(dbImGroupChat.getSetting(), ImGroupSetting.class));
        imGroupChat.setId(new ImChatId(dbImChat.getChatId()));
        imGroupChat.setName(new ImChatName(dbImChat.getName()));
        imGroupChat.setType(ImChatType.GROUP);
        imGroupChat.setIncrId(dbImChat.getId());
        return imGroupChat;
    }

    List<ImGroupMember> imGroupMemberListFrom(List<DbImGroupMember> dbImGroupMemberList);

    default ImGroupMember imGroupMemberFrom(DbImGroupMember dbImGroupMember) {
        ImGroupMember imGroupMember = new ImGroupMember();
        imGroupMember.setUserId(new UserId(dbImGroupMember.getUserId()));
        imGroupMember.setGroupAlias(new ImGroupAlias(dbImGroupMember.getGroupAlias()));
        imGroupMember.setUserAlias(new ImGroupUserAlias(dbImGroupMember.getUserAlias()));
        imGroupMember.setSetting(JsonUtils.fromJson(dbImGroupMember.getSetting(), ImGroupMemberSetting.class));
        return imGroupMember;
    }

    @Mappings(value = {
            @Mapping(target = "chatId.value", source = "chatId"),
            @Mapping(target = "message.id.value", source = "id"),
            @Mapping(target = "message.token.value", source = "token"),
            @Mapping(target = "message.content.type", source = "type"),
            @Mapping(target = "message.content.value", source = "content"),
            @Mapping(target = "message.chatId.value", source = "chatId"),
            @Mapping(target = "message.senderId.value", source = "senderId"),
            @Mapping(target = "message.sendTime", source = "sendTime"),
            @Mapping(target = "message.revokeTime", source = "revokeTime"),
    })
    ImChatLastMessage imChatLastMessageFrom(DbImPrivateMessage dbImPrivateMessage);

    @Mappings(value = {
            @Mapping(target = "chatId.value", source = "chatId"),
            @Mapping(target = "message.id.value", source = "id"),
            @Mapping(target = "message.token.value", source = "token"),
            @Mapping(target = "message.content.type", source = "type"),
            @Mapping(target = "message.content.value", source = "content"),
            @Mapping(target = "message.chatId.value", source = "chatId"),
            @Mapping(target = "message.senderId.value", source = "senderId"),
            @Mapping(target = "message.sendTime", source = "sendTime"),
            @Mapping(target = "message.revokeTime", source = "revokeTime"),
    })
    ImChatLastMessage imChatLastMessageFrom(DbImGroupMessage dbImGroupMessage);

    @ValueMappings(value = {
            @ValueMapping(source = "NONE", target = "PRIVATE"),
            @ValueMapping(source = "PRIVATE", target = "PRIVATE"),
            @ValueMapping(source = "GROUP", target = "GROUP")
    })
    ImChatType imChatTypeFrom(DbImChatType type);

    @ValueMappings(value = {
            @ValueMapping(target = MappingConstants.NULL, source = "NONE"),
            @ValueMapping(target = "SENT", source = "SENT"),
            @ValueMapping(target = "RECEIVED", source = "RECEIVED"),
            @ValueMapping(target = "READ", source = "READ"),
            @ValueMapping(target = "REVOKED", source = "REVOKED")
    })
    ImPrivateMessageStatus imPrivateMessageStatusFrom(DbPrivateImMessageStatus dbStatus);
}