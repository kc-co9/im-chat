package com.co.kc.imchat.service.message.transformer.domain;

import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatStatus;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatType;
import com.co.kc.imchat.common.domain.group.model.GroupAlias;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.enums.DbImChatType;
import com.co.kc.imchat.service.message.infrastructure.mybatis.enums.DbImChatStatus;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.mapstruct.factory.Mappers;
import org.apache.commons.lang3.StringUtils;

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
        return ImPrivateChat.builder()
                .pkId(dbImPrivateChat.getId())
                .userId(new UserId(dbImPrivateChat.getUserId()))
                .peerUserId(new UserId(dbImPrivateChat.getPeerUserId()))
                .id(new ImChatId(dbImPrivateChat.getChatId()))
                .type(ImChatType.PRIVATE)
                .status(imChatStatusFrom(dbImPrivateChat.getStatus()))
                .activeTime(dbImPrivateChat.getActiveTime())
                .lastMessageId(messageIdFrom(dbImPrivateChat.getLastMessageId()))
                .readMessageId(messageIdFrom(dbImPrivateChat.getReadMessageId()))
                .unreadMessageCount(dbImPrivateChat.getUnreadMessageCount())
                .build();
    }

    List<ImGroupChat> imGroupChatListFrom(List<DbImGroupChat> dbImGroupChatList);

    default ImGroupChat imGroupChatFrom(DbImGroupChat dbImGroupChat) {
        return ImGroupChat.builder()
                .pkId(dbImGroupChat.getId())
                .id(new ImChatId(dbImGroupChat.getChatId()))
                .type(ImChatType.GROUP)
                .groupId(new GroupId(dbImGroupChat.getGroupId()))
                .userId(new UserId(dbImGroupChat.getUserId()))
                .groupAlias(StringUtils.isBlank(dbImGroupChat.getGroupAlias()) ? null : new GroupAlias(dbImGroupChat.getGroupAlias()))
                .status(imChatStatusFrom(dbImGroupChat.getStatus()))
                .activeTime(dbImGroupChat.getActiveTime())
                .lastMessageId(messageIdFrom(dbImGroupChat.getLastMessageId()))
                .readMessageId(messageIdFrom(dbImGroupChat.getReadMessageId()))
                .unreadMessageCount(dbImGroupChat.getUnreadMessageCount())
                .build();
    }

    default ImMessageId messageIdFrom(Long value) {
        if (value == null || value <= 0) {
            return null;
        }
        return new ImMessageId(value);
    }

    @ValueMappings(value = {
            @ValueMapping(source = "NONE", target = "PRIVATE"),
            @ValueMapping(source = "PRIVATE", target = "PRIVATE"),
            @ValueMapping(source = "GROUP", target = "GROUP")
    })
    ImChatType imChatTypeFrom(DbImChatType type);

    @ValueMapping(source = MappingConstants.NULL, target = "UNKNOWN")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = "UNKNOWN")
    ImChatStatus imChatStatusFrom(DbImChatStatus status);

}
