package com.co.kc.imchat.service.message.transformer;

import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.common.domain.group.model.MemberDisplayName;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.domain.chat.model.GroupChatMember;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImUserChatDescriptor;
import com.co.kc.imchat.service.message.facade.dto.UserGroupChatSummaryDTO;
import com.co.kc.imchat.service.message.facade.params.GroupChatMemberDescriptorParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatMemberParams;
import com.co.kc.imchat.service.message.model.cqrs.dto.group.GroupChatOpenDTO;
import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImPrivateChatOpenDTO;
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ImChatAppTransformer {
    ImChatAppTransformer INSTANCE = Mappers.getMapper(ImChatAppTransformer.class);

    List<ImChatItemDTO> imChatListFrom(List<ImUserChatDescriptor> imUserChatDescriptors);

    @Mappings(value = {
            @Mapping(target = "chatId", source = "chatId.value"),
            @Mapping(target = "chatName", source = "chatName.value"),
            @Mapping(target = "chatType", source = "chatType"),
            @Mapping(target = "lastMessageType", source = "chatLastMessage.content.type"),
            @Mapping(target = "lastMessageContent", source = "chatLastMessage.visibleContent"),
            @Mapping(target = "lastMessageTime", source = "chatLastMessage.sendTime"),
    })
    ImChatItemDTO imChatItemDtoFrom(ImUserChatDescriptor imUserChatDescriptors);

    default List<GroupChatMember> groupChatMembersFrom(GroupId fallbackGroupId, List<GroupChatMemberParams> members) {
        return CollectionUtils.emptyIfNull(members).stream()
                .map(member -> groupChatMemberFrom(fallbackGroupId, member))
                .toList();
    }

    default GroupChatMember groupChatMemberFrom(GroupId fallbackGroupId, GroupChatMemberParams member) {
        GroupId groupId = member.groupId() == null ? fallbackGroupId : new GroupId(member.groupId());
        UserId userId = new UserId(member.userId());
        return new GroupChatMember(groupId, userId);
    }

    default List<MemberDescriptor> memberDescriptorsFrom(List<GroupChatMemberDescriptorParams> descriptors) {
        return CollectionUtils.emptyIfNull(descriptors).stream()
                .map(this::memberDescriptorFrom)
                .toList();
    }

    default MemberDescriptor memberDescriptorFrom(GroupChatMemberDescriptorParams descriptor) {
        MemberDisplayName displayName =
                descriptor.displayName() == null ? null : new MemberDisplayName(descriptor.displayName());
        return new MemberDescriptor(new UserId(descriptor.userId()), displayName, descriptor.joinTime());
    }

    default UserGroupChatSummaryDTO userGroupChatSummaryDtoFrom(ImGroupChat chat) {
        return new UserGroupChatSummaryDTO(
                true,
                chat.getId().value(),
                chat.getGroupId().value(),
                chat.getUserId().value(),
                chat.getUnreadMessageCount(),
                chat.getActiveTime());
    }

    default ImPrivateChatOpenDTO imPrivateChatOpenDtoFrom(ImPrivateChat chat, UserId peerUserId) {
        return new ImPrivateChatOpenDTO(chat.getId().value(), peerUserId.value());
    }

    default GroupChatOpenDTO groupChatOpenDtoFrom(ImGroupChat chat) {
        return new GroupChatOpenDTO(chat.getId().value(), chat.getGroupId().value());
    }
}
