package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupAlias;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.chat.ImGroupName;
import com.co.kc.imchat.domain.chat.ImGroupNotification;
import com.co.kc.imchat.domain.chat.ImGroupUserAlias;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivatePair;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import org.mapstruct.Mapper;
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

    List<ImPrivateChat> imPrivateChatListFrom(List<DbImPrivateChat> dbImPrivateChats);

    default ImPrivateChat imPrivateChatFrom(DbImPrivateChat dbImPrivateChat) {
        ImPrivateChat imPrivateChat = new ImPrivateChat();
        imPrivateChat.setPair(new ImPrivatePair(new UserId(dbImPrivateChat.getMember1()), new UserId(dbImPrivateChat.getMember2())));
        imPrivateChat.setId(new ImChatId(dbImPrivateChat.getChatId()));
        imPrivateChat.setType(ImChatType.PRIVATE);
        imPrivateChat.setIncrId(dbImPrivateChat.getId());
        return imPrivateChat;
    }

    List<ImGroupChat> imGroupChatListFrom(List<DbImGroupChat> dbImGroupChatList);

    default ImGroupChat imGroupChatFrom(DbImGroupChat dbImGroupChat) {
        ImGroupChat imGroupChat = new ImGroupChat();
        imGroupChat.setOwnerId(new UserId(dbImGroupChat.getOwnerId()));
        imGroupChat.setNotification(new ImGroupNotification(dbImGroupChat.getNotification()));
        imGroupChat.setId(new ImChatId(dbImGroupChat.getChatId()));
        imGroupChat.setName(new ImGroupName(dbImGroupChat.getName()));
        imGroupChat.setType(ImChatType.GROUP);
        imGroupChat.setIncrId(dbImGroupChat.getId());
        return imGroupChat;
    }


    List<ImGroupMember> imGroupMemberListFrom(List<DbImGroupMember> dbImGroupMemberList);

    default ImGroupMember imGroupMemberFrom(DbImGroupMember dbImGroupMember) {
        ImGroupMember imGroupMember = new ImGroupMember();
        imGroupMember.setUserId(new UserId(dbImGroupMember.getUserId()));
        imGroupMember.setGroupAlias(new ImGroupAlias(dbImGroupMember.getGroupAlias()));
        imGroupMember.setUserAlias(new ImGroupUserAlias(dbImGroupMember.getUserAlias()));
        return imGroupMember;
    }

    @ValueMappings(value = {
            @ValueMapping(source = "NONE", target = "PRIVATE"),
            @ValueMapping(source = "PRIVATE", target = "PRIVATE"),
            @ValueMapping(source = "GROUP", target = "GROUP")
    })
    ImChatType imChatTypeFrom(DbImChatType type);

}