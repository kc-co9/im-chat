package com.co.kc.imchat.infrastructure.transformer.db;

import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupStatus;
import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.chat.model.ImChatStatus;
import com.co.kc.imchat.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import org.mapstruct.Mapper;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatStatus;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImGroupStatus;
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

    default DbImGroup dbImGroupFrom(Group group) {
        DbImGroup dbGroup = new DbImGroup();
        dbGroup.setId(group.getPkId());
        dbGroup.setGroupId(group.getId().value());
        dbGroup.setOwnerId(group.getOwnerId().value());
        dbGroup.setName(group.getName().value());
        dbGroup.setNotification(group.getNotification() == null ? "" : group.getNotification().value());
        dbGroup.setMemberCount(group.getMemberCount().value());
        dbGroup.setStatus(dbImGroupStatusFrom(group.getStatus()));
        return dbGroup;
    }

    List<DbImGroupChat> dbImGroupChatListFrom(List<ImGroupChat> groupChats);

    List<DbImGroupMember> dbImGroupMemberListFrom(List<GroupMember> members);

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

    default DbImGroupMember dbImGroupMemberFrom(GroupMember member) {
        DbImGroupMember dbGroupMember = new DbImGroupMember();
        dbGroupMember.setId(member.getPkId());
        dbGroupMember.setGroupId(member.getGroupId().value());
        dbGroupMember.setUserId(member.getUserId().value());
        dbGroupMember.setUserAlias(member.getUserAlias() == null ? "" : member.getUserAlias().value());
        dbGroupMember.setJoinTime(member.getJoinTime());
        return dbGroupMember;
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

    @ValueMappings(value = {
            @ValueMapping(source = "ACTIVE", target = "NORMAL"),
            @ValueMapping(source = "DISMISSED", target = "DISMISSED")
    })
    DbImGroupStatus dbImGroupStatusFrom(GroupStatus status);

}
