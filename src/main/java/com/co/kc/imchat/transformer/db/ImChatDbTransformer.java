package com.co.kc.imchat.transformer.db;

import com.co.kc.imchat.domain.chat.ImGroup;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroup;
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

    default DbImGroup dbImGroupFrom(ImGroup group) {
        DbImGroup dbGroup = new DbImGroup();
        dbGroup.setId(group.getPkId());
        dbGroup.setGroupId(group.getId().getValue());
        dbGroup.setOwnerId(group.getOwnerId().getValue());
        dbGroup.setName(group.getName().getValue());
        dbGroup.setNotification(group.getNotification() == null ? "" : group.getNotification().getValue());
        return dbGroup;
    }

    List<DbImGroupChat> dbImGroupChatListFrom(List<ImGroupChat> groupChats);

    List<DbImGroupMember> dbImGroupMemberListFrom(List<ImGroupMember> members);

    default DbImGroupChat dbImGroupChatFrom(ImGroupChat groupChat) {
        DbImGroupChat dbGroupChat = new DbImGroupChat();
        dbGroupChat.setId(groupChat.getPkId());
        dbGroupChat.setChatId(groupChat.getId().getValue());
        dbGroupChat.setGroupId(groupChat.getGroupId().getValue());
        dbGroupChat.setUserId(groupChat.getUserId().getValue());
        dbGroupChat.setGroupAlias(groupChat.getGroupAlias() == null ? "" : groupChat.getGroupAlias().getValue());
        dbGroupChat.setLastMessageId(groupChat.getLastMessageId() == null ? 0L : groupChat.getLastMessageId().getValue());
        dbGroupChat.setReadMessageId(groupChat.getReadMessageId() == null ? 0L : groupChat.getReadMessageId().getValue());
        dbGroupChat.setUnreadMessageCount(groupChat.getUnreadMessageCount() == null ? 0 : groupChat.getUnreadMessageCount());
        return dbGroupChat;
    }

    default DbImGroupMember dbImGroupMemberFrom(ImGroupMember member) {
        DbImGroupMember dbGroupMember = new DbImGroupMember();
        dbGroupMember.setId(member.getPkId());
        dbGroupMember.setGroupId(member.getGroupId().getValue());
        dbGroupMember.setUserId(member.getUserId().getValue());
        dbGroupMember.setUserAlias(member.getUserAlias() == null ? "" : member.getUserAlias().getValue());
        dbGroupMember.setJoinTime(member.getJoinTime());
        return dbGroupMember;
    }

    @ValueMappings(value = {
            @ValueMapping(source = "PRIVATE", target = "PRIVATE"),
            @ValueMapping(source = "GROUP", target = "GROUP")
    })
    DbImChatType dbImChatTypeFrom(ImChatType type);

}
