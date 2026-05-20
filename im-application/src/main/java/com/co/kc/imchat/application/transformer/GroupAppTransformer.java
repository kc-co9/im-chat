package com.co.kc.imchat.application.transformer;

import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.domain.group.model.UserGroupDescriptor;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupDetailDTO;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupItemDTO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface GroupAppTransformer {
    GroupAppTransformer INSTANCE = Mappers.getMapper(GroupAppTransformer.class);

    default List<GroupItemDTO> groupDtoListFrom(List<UserGroupDescriptor> userGroupDescriptors) {
        return userGroupDescriptors.stream()
                .map(this::groupItemDtoFrom)
                .collect(Collectors.toList());
    }

    default GroupItemDTO groupItemDtoFrom(UserGroupDescriptor descriptor) {
        GroupItemDTO dto = new GroupItemDTO();
        dto.setGroupId(descriptor.getId().getValue());
        dto.setChatId(descriptor.getChat().getId().getValue());
        dto.setGroupName(descriptor.getName().getValue());
        dto.setMemberCount(descriptor.getMemberCount().getValue());
        dto.setUnreadMessageCount(descriptor.getChat().getUnreadMessageCount());
        return dto;
    }

    default GroupItemDTO groupItemDtoFrom(Group group, ImGroupChat groupChat) {
        GroupItemDTO dto = new GroupItemDTO();
        dto.setGroupId(groupChat.getGroupId().getValue());
        dto.setChatId(groupChat.getId().getValue());
        if (group != null) {
            dto.setGroupName(group.getName().getValue());
            dto.setMemberCount(group.getMemberCount().getValue());
        }
        dto.setUnreadMessageCount(groupChat.getUnreadMessageCount());
        return dto;
    }

    default GroupDetailDTO groupDetailDtoFrom(Group group, ImGroupChat groupChat, List<MemberDescriptor> members) {
        GroupDetailDTO dto = new GroupDetailDTO();
        dto.setGroupId(group.getId().getValue());
        dto.setChatId(groupChat.getId().getValue());
        dto.setGroupName(group.getName().getValue());
        dto.setOwnerId(group.getOwnerId().getValue());
        dto.setNotification(group.getNotification() == null ? null : group.getNotification().getValue());
        dto.setMemberCount(group.getMemberCount().getValue());
        dto.setMembers(members.stream().map(this::groupMemberDtoFrom).collect(Collectors.toList()));
        return dto;
    }

    default GroupDetailDTO.Member groupMemberDtoFrom(MemberDescriptor member) {
        GroupDetailDTO.Member dto = new GroupDetailDTO.Member();
        dto.setUserId(member.getUserId().getValue());
        dto.setDisplayName(member.getDisplayName().getValue());
        dto.setJoinTime(member.getJoinTime());
        return dto;
    }
}
