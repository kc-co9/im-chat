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
        dto.setGroupId(descriptor.id().value());
        dto.setChatId(descriptor.chat().getId().value());
        dto.setGroupName(descriptor.name().value());
        dto.setMemberCount(descriptor.memberCount().value());
        dto.setUnreadMessageCount(descriptor.chat().getUnreadMessageCount());
        return dto;
    }

    default GroupItemDTO groupItemDtoFrom(Group group, ImGroupChat groupChat) {
        GroupItemDTO dto = new GroupItemDTO();
        dto.setGroupId(groupChat.getGroupId().value());
        dto.setChatId(groupChat.getId().value());
        if (group != null) {
            dto.setGroupName(group.getName().value());
            dto.setMemberCount(group.getMemberCount().value());
        }
        dto.setUnreadMessageCount(groupChat.getUnreadMessageCount());
        return dto;
    }

    default GroupDetailDTO groupDetailDtoFrom(Group group, ImGroupChat groupChat, List<MemberDescriptor> members) {
        GroupDetailDTO dto = new GroupDetailDTO();
        dto.setGroupId(group.getId().value());
        dto.setChatId(groupChat.getId().value());
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
        dto.setDisplayName(member.displayName().value());
        dto.setJoinTime(member.joinTime());
        return dto;
    }
}
