package com.co.kc.imchat.service.message.transformer.db;

import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatStatus;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImPrivateChat;
import org.mapstruct.Mapper;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

import com.co.kc.imchat.service.message.domain.chat.model.ImChatType;
import com.co.kc.imchat.service.message.infrastructure.mybatis.enums.DbImChatStatus;
import com.co.kc.imchat.service.message.infrastructure.mybatis.enums.DbImChatType;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ImChatDbTransformer {
    ImChatDbTransformer INSTANCE = Mappers.getMapper(ImChatDbTransformer.class);

    default DbImPrivateChat dbImPrivateChatFrom(ImPrivateChat imPrivateChat) {
        DbImPrivateChat row = new DbImPrivateChat();
        row.setId(imPrivateChat.getPkId());
        row.setChatId(imPrivateChat.getId().value());
        row.setUserId(imPrivateChat.getUserId().value());
        row.setPeerUserId(imPrivateChat.getPeerUserId().value());
        row.setLastMessageId(imPrivateChat.getLastMessageId() == null ? 0L : imPrivateChat.getLastMessageId().value());
        row.setReadMessageId(imPrivateChat.getReadMessageId() == null ? 0L : imPrivateChat.getReadMessageId().value());
        row.setUnreadMessageCount(imPrivateChat.getUnreadMessageCount() == null ? 0 : imPrivateChat.getUnreadMessageCount());
        row.setStatus(dbImChatStatusFrom(imPrivateChat.getStatus()));
        row.setActiveTime(imPrivateChat.getActiveTime());
        return row;
    }

    List<DbImGroupChat> dbImGroupChatListFrom(List<ImGroupChat> groupChats);

    default DbImGroupChat dbImGroupChatFrom(ImGroupChat groupChat) {
        DbImGroupChat dbGroupChat = new DbImGroupChat();
        dbGroupChat.setId(groupChat.getPkId());
        dbGroupChat.setChatId(groupChat.getId().value());
        dbGroupChat.setGroupId(groupChat.getGroupId().value());
        dbGroupChat.setUserId(groupChat.getUserId().value());
        dbGroupChat.setGroupAlias(groupChat.getGroupAlias() == null ? "" : groupChat.getGroupAlias().value());
        dbGroupChat.setLastMessageId(groupChat.getLastMessageId() == null ? 0L : groupChat.getLastMessageId().value());
        dbGroupChat.setReadMessageId(groupChat.getReadMessageId() == null ? 0L : groupChat.getReadMessageId().value());
        dbGroupChat.setUnreadMessageCount(groupChat.getUnreadMessageCount() == null ? 0 : groupChat.getUnreadMessageCount());
        dbGroupChat.setStatus(dbImChatStatusFrom(groupChat.getStatus()));
        dbGroupChat.setActiveTime(groupChat.getActiveTime());
        return dbGroupChat;
    }

    @ValueMappings(value = {
            @ValueMapping(source = "PRIVATE", target = "PRIVATE"),
            @ValueMapping(source = "GROUP", target = "GROUP")
    })
    DbImChatType dbImChatTypeFrom(ImChatType type);

    default DbImChatStatus dbImChatStatusFrom(ImChatStatus status) {
        if (status == null) {
            return DbImChatStatus.UNKNOWN;
        }
        return switch (status) {
            case NORMAL -> DbImChatStatus.NORMAL;
            case HIDDEN -> DbImChatStatus.HIDDEN;
            default -> DbImChatStatus.UNKNOWN;
        };
    }

}
