package com.co.kc.imchat.service.social.transformer;

import com.co.kc.imchat.service.social.domain.group.model.Group;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.service.social.facade.dto.GroupMemberCheckDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMemberItemDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMembersDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMessageRecipientDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMessageRecipientsDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupSummariesDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupSummaryDTO;
import com.co.kc.imchat.service.social.model.cqrs.dto.group.GroupCreateDTO;
import com.co.kc.imchat.service.social.model.cqrs.dto.group.GroupDetailDTO;
import com.co.kc.imchat.service.social.model.cqrs.dto.group.GroupItemDTO;
import com.co.kc.imchat.service.social.domain.message.model.UserGroupChatSummary;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface GroupAppTransformer {
    GroupAppTransformer INSTANCE = Mappers.getMapper(GroupAppTransformer.class);

    default GroupItemDTO groupItemDtoFrom(Group group, UserGroupChatSummary groupChat) {
        GroupItemDTO dto = new GroupItemDTO();
        if (group != null) {
            dto.setGroupId(group.getId().value());
            dto.setGroupName(group.getName().value());
            dto.setMemberCount(group.getMemberCount().value());
        }
        if (groupChat != null) {
            dto.setGroupId(groupChat.groupId());
            dto.setChatId(groupChat.chatId());
            dto.setUnreadMessageCount(groupChat.unreadMessageCount());
        }
        return dto;
    }

    default GroupDetailDTO groupDetailDtoFrom(Group group, UserGroupChatSummary groupChat, List<MemberDescriptor> members) {
        GroupDetailDTO dto = new GroupDetailDTO();
        dto.setGroupId(group.getId().value());
        dto.setChatId(groupChat.chatId());
        dto.setGroupName(group.getName().value());
        dto.setOwnerId(group.getOwnerId().value());
        dto.setNotification(group.getNotification() == null ? null : group.getNotification().value());
        dto.setMemberCount(group.getMemberCount().value());
        dto.setMembers(members.stream().map(this::groupMemberDtoFrom).collect(Collectors.toList()));
        return dto;
    }

    default GroupDetailDTO.Member groupMemberDtoFrom(MemberDescriptor member) {
        GroupDetailDTO.Member dto = new GroupDetailDTO.Member();
        dto.setUserId(member.userId().value());
        dto.setDisplayName(member.displayName() == null ? null : member.displayName().value());
        dto.setJoinTime(member.joinTime());
        return dto;
    }

    default GroupMemberItemDTO groupMemberItemDtoFrom(GroupMember member) {
        String alias = member.getUserAlias() == null ? null : member.getUserAlias().value();
        return new GroupMemberItemDTO(member.getUserId().value(), alias);
    }

    default GroupMessageRecipientDTO groupMessageRecipientDtoFrom(GroupMember member) {
        return new GroupMessageRecipientDTO(member.getUserId().value());
    }

    default GroupSummaryDTO groupSummaryDtoFrom(Group group) {
        return new GroupSummaryDTO(
                group.getId().value(),
                group.getName().value(),
                !group.isDismissed());
    }

    default GroupCreateDTO groupCreateDtoFrom(GroupId groupId) {
        return new GroupCreateDTO(groupId.value());
    }

    default GroupMemberCheckDTO groupMemberCheckDtoFrom(boolean member) {
        return new GroupMemberCheckDTO(member);
    }

    default GroupMembersDTO groupMembersDtoFrom(GroupId groupId, List<GroupMemberItemDTO> members) {
        return new GroupMembersDTO(groupId.value(), members);
    }

    default GroupMessageRecipientsDTO groupMessageRecipientsDtoFrom(GroupId groupId,
                                                                    List<GroupMessageRecipientDTO> recipients) {
        return new GroupMessageRecipientsDTO(groupId.value(), recipients);
    }

    default GroupSummariesDTO groupSummariesDtoFrom(List<GroupSummaryDTO> groups) {
        return new GroupSummariesDTO(groups);
    }
}
