package com.co.kc.imchat.transformer.application;

import com.co.kc.imchat.domain.group.ImGroup;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.group.ImUserGroupDescriptor;
import com.co.kc.imchat.domain.group.ImGroupMember;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupDetailDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupItemDTO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface GroupAppTransformer {
    GroupAppTransformer INSTANCE = Mappers.getMapper(GroupAppTransformer.class);

    default List<ImGroupItemDTO> imGroupDtoListFrom(List<ImUserGroupDescriptor> imUserGroupDescriptors) {
        return imUserGroupDescriptors.stream()
                .map(this::imGroupItemDtoFrom)
                .collect(Collectors.toList());
    }

    default ImGroupItemDTO imGroupItemDtoFrom(ImUserGroupDescriptor descriptor) {
        ImGroupItemDTO dto = new ImGroupItemDTO();
        dto.setGroupId(descriptor.getId().getValue());
        dto.setChatId(descriptor.getChat().getId().getValue());
        dto.setGroupName(descriptor.getName().getValue());
        dto.setUnreadMessageCount(descriptor.getChat().getUnreadMessageCount());
        return dto;
    }

    default ImGroupItemDTO imGroupItemDtoFrom(ImGroup group, ImGroupChat groupChat, int memberCount) {
        ImGroupItemDTO dto = new ImGroupItemDTO();
        dto.setGroupId(groupChat.getGroupId().getValue());
        dto.setChatId(groupChat.getId().getValue());
        if (group != null) {
            dto.setGroupName(group.getName().getValue());
        }
        dto.setUnreadMessageCount(groupChat.getUnreadMessageCount());
        return dto;
    }

    default ImGroupDetailDTO imGroupDetailDtoFrom(ImGroup group, ImGroupChat groupChat, List<ImGroupMember> members) {
        ImGroupDetailDTO dto = new ImGroupDetailDTO();
        dto.setGroupId(group.getId().getValue());
        dto.setChatId(groupChat.getId().getValue());
        dto.setGroupName(group.getName().getValue());
        dto.setOwnerId(group.getOwnerId().getValue());
        dto.setNotification(group.getNotification() == null ? null : group.getNotification().getValue());
        dto.setMemberCount(members.size());
        dto.setMembers(members.stream().map(this::imGroupMemberDtoFrom).collect(Collectors.toList()));
        return dto;
    }

    default ImGroupDetailDTO.Member imGroupMemberDtoFrom(ImGroupMember member) {
        ImGroupDetailDTO.Member dto = new ImGroupDetailDTO.Member();
        dto.setUserId(member.getUserId().getValue());
        dto.setUserAlias(member.getUserAlias() == null ? null : member.getUserAlias().getValue());
        dto.setJoinTime(member.getJoinTime());
        return dto;
    }
}
