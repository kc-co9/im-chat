package com.co.kc.imchat.infrastructure.transformer.domain;

import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.chat.model.ImChatStatus;
import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupAlias;
import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.model.MemberId;
import com.co.kc.imchat.domain.group.model.GroupName;
import com.co.kc.imchat.domain.group.model.GroupNotification;
import com.co.kc.imchat.domain.group.model.GroupStatus;
import com.co.kc.imchat.domain.group.model.GroupUserAlias;
import com.co.kc.imchat.domain.group.model.MemberCount;
import com.co.kc.imchat.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatStatus;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImGroupStatus;
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
                .status(imChatStatusFrom(dbImPrivateChat.getStatus()))
                .activeTime(dbImPrivateChat.getActiveTime())
                .lastMessageId(messageIdFrom(dbImPrivateChat.getLastMessageId()))
                .readMessageId(messageIdFrom(dbImPrivateChat.getReadMessageId()))
                .unreadMessageCount(dbImPrivateChat.getUnreadMessageCount())
                .build();
    }

    List<Group> imGroupListFrom(List<DbImGroup> dbImGroupList);

    default Group imGroupFrom(DbImGroup dbImGroup) {
        Group group = Group.builder()
                .id(new GroupId(dbImGroup.getGroupId()))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(dbImGroup.getOwnerId()))
                .name(new GroupName(dbImGroup.getName()))
                .notification(new GroupNotification(dbImGroup.getNotification()))
                .memberCount(new MemberCount(dbImGroup.getMemberCount() == null ? 0 : dbImGroup.getMemberCount()))
                .status(imGroupStatusFrom(dbImGroup.getStatus()))
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

    List<GroupMember> imGroupMemberListFrom(List<DbImGroupMember> dbImGroupMembers);

    default GroupMember imGroupMemberFrom(DbImGroupMember dbImGroupMember) {
        GroupId groupId = new GroupId(dbImGroupMember.getGroupId());
        UserId userId = new UserId(dbImGroupMember.getUserId());
        return GroupMember.builder()
                .pkId(dbImGroupMember.getId())
                .id(new MemberId(groupId, userId))
                .groupId(groupId)
                .userId(userId)
                .userAlias(StringUtils.isBlank(dbImGroupMember.getUserAlias()) ? null : new GroupUserAlias(dbImGroupMember.getUserAlias()))
                .joinTime(dbImGroupMember.getJoinTime())
                .build();
    }

    @ValueMappings(value = {
            @ValueMapping(source = "NONE", target = "PRIVATE"),
            @ValueMapping(source = "PRIVATE", target = "PRIVATE"),
            @ValueMapping(source = "GROUP", target = "GROUP")
    })
    ImChatType imChatTypeFrom(DbImChatType type);

    default ImChatStatus imChatStatusFrom(DbImChatStatus status) {
        if (status == null) {
            return ImChatStatus.UNKNOWN;
        }
        switch (status) {
            case NORMAL:
                return ImChatStatus.NORMAL;
            case HIDDEN:
                return ImChatStatus.HIDDEN;
            case UNKNOWN:
            default:
                return ImChatStatus.UNKNOWN;
        }
    }

    default GroupStatus imGroupStatusFrom(DbImGroupStatus status) {
        if (status == null || status == DbImGroupStatus.NONE) {
            throw new IllegalStateException("群状态不能为未知");
        }
        switch (status) {
            case NORMAL:
                return GroupStatus.ACTIVE;
            case DISMISSED:
                return GroupStatus.DISMISSED;
            default:
                throw new IllegalStateException("不支持的群状态：" + status);
        }
    }

}
