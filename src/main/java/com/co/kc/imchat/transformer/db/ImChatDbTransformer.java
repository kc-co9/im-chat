package com.co.kc.imchat.transformer.db;

import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import org.mapstruct.Mapper;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ImChatDbTransformer {
    ImChatDbTransformer INSTANCE = Mappers.getMapper(ImChatDbTransformer.class);

    default DbImPrivateChat dbImPrivateChatFrom(ImPrivateChat imPrivateChat) {
        DbImPrivateChat row = new DbImPrivateChat();
        row.setId(imPrivateChat.getPkId());
        row.setChatId(imPrivateChat.getId().getValue());
        row.setUserId(imPrivateChat.getUserId().getValue());
        row.setPeerUserId(imPrivateChat.getPeerUserId().getValue());
        row.setLastMessageId(imPrivateChat.getLastMessageId() == null ? 0L : imPrivateChat.getLastMessageId().getValue());
        row.setReadMessageId(imPrivateChat.getReadMessageId() == null ? 0L : imPrivateChat.getReadMessageId().getValue());
        row.setUnreadMessageCount(imPrivateChat.getUnreadMessageCount() == null ? 0 : imPrivateChat.getUnreadMessageCount());
        return row;
    }

    default DbImGroupChat dbImGroupChatFrom(ImGroupChat imGroupChat) {
        DbImGroupChat dbImGroupChat = new DbImGroupChat();
        dbImGroupChat.setChatId(imGroupChat.getId().getValue());
        dbImGroupChat.setOwnerId(imGroupChat.getOwnerId().getValue());
        dbImGroupChat.setNotification(imGroupChat.getNotification().getValue());
        return dbImGroupChat;
    }

    List<DbImGroupMember> dbImGroupMemberListFrom(List<ImGroupMember> imGroupMembers);

    default DbImGroupMember dbImGroupMemberFrom(ImGroupMember imGroupMember) {
        DbImGroupMember dbImGroupMember = new DbImGroupMember();
        dbImGroupMember.setChatId(imGroupMember.getChatId().getValue());
        dbImGroupMember.setUserId(imGroupMember.getUserId().getValue());
        dbImGroupMember.setUserAlias(imGroupMember.getUserAlias().getValue());
        dbImGroupMember.setGroupAlias(imGroupMember.getGroupAlias().getValue());
        return dbImGroupMember;
    }

    @ValueMappings(value = {
            @ValueMapping(source = "PRIVATE", target = "PRIVATE"),
            @ValueMapping(source = "GROUP", target = "GROUP")
    })
    DbImChatType dbImChatTypeFrom(ImChatType type);

}
