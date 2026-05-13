package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroup;
import com.co.kc.imchat.domain.chat.ImGroupAlias;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupId;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.chat.ImGroupMemberId;
import com.co.kc.imchat.domain.chat.ImGroupName;
import com.co.kc.imchat.domain.chat.ImGroupNotification;
import com.co.kc.imchat.domain.chat.ImGroupUserAlias;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import org.mapstruct.Mapper;
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
                .lastMessageId(new ImMessageId(dbImPrivateChat.getLastMessageId()))
                .readMessageId(new ImMessageId(dbImPrivateChat.getReadMessageId()))
                .unreadMessageCount(dbImPrivateChat.getUnreadMessageCount())
                .build();
    }

    List<ImGroup> imGroupListFrom(List<DbImGroup> dbImGroupList);

    default ImGroup imGroupFrom(DbImGroup dbImGroup) {
        ImGroup group = ImGroup.builder()
                .id(new ImGroupId(dbImGroup.getGroupId()))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(dbImGroup.getOwnerId()))
                .name(new ImGroupName(dbImGroup.getName()))
                .notification(new ImGroupNotification(dbImGroup.getNotification()))
                .build();
        group.setPkId(dbImGroup.getId());
        return group;
    }

    List<ImGroupChat> imGroupChatListFrom(List<DbImGroupChat> dbImGroupChatList);

    default ImGroupChat imGroupChatFrom(DbImGroupChat dbImGroupChat) {
        return ImGroupChat.builder()
                .pkId(dbImGroupChat.getId())
                .id(new ImChatId(dbImGroupChat.getChatId()))
                .type(ImChatType.GROUP)
                .groupId(new ImGroupId(dbImGroupChat.getGroupId()))
                .userId(new UserId(dbImGroupChat.getUserId()))
                .groupAlias(StringUtils.isBlank(dbImGroupChat.getGroupAlias()) ? null : new ImGroupAlias(dbImGroupChat.getGroupAlias()))
                .lastMessageId(new ImMessageId(dbImGroupChat.getLastMessageId()))
                .readMessageId(new ImMessageId(dbImGroupChat.getReadMessageId()))
                .unreadMessageCount(dbImGroupChat.getUnreadMessageCount())
                .build();
    }

    List<ImGroupMember> imGroupMemberListFrom(List<DbImGroupMember> dbImGroupMembers);

    default ImGroupMember imGroupMemberFrom(DbImGroupMember dbImGroupMember) {
        ImGroupId groupId = new ImGroupId(dbImGroupMember.getGroupId());
        UserId userId = new UserId(dbImGroupMember.getUserId());
        return ImGroupMember.builder()
                .pkId(dbImGroupMember.getId())
                .id(new ImGroupMemberId(groupId, userId))
                .groupId(groupId)
                .userId(userId)
                .userAlias(new ImGroupUserAlias(dbImGroupMember.getUserAlias()))
                .joinTime(dbImGroupMember.getJoinTime())
                .build();
    }

    @ValueMappings(value = {
            @ValueMapping(source = "NONE", target = "PRIVATE"),
            @ValueMapping(source = "PRIVATE", target = "PRIVATE"),
            @ValueMapping(source = "GROUP", target = "GROUP")
    })
    ImChatType imChatTypeFrom(DbImChatType type);

}
