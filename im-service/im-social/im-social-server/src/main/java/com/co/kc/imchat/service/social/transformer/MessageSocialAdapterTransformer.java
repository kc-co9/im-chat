package com.co.kc.imchat.service.social.transformer;

import com.co.kc.imchat.common.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.service.message.facade.dto.UserGroupChatSummaryDTO;
import com.co.kc.imchat.service.message.facade.params.GroupChatMemberDescriptorParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatMemberParams;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.service.social.domain.message.model.UserGroupChatSummary;
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface MessageSocialAdapterTransformer {
    MessageSocialAdapterTransformer INSTANCE = Mappers.getMapper(MessageSocialAdapterTransformer.class);

    default List<GroupChatMemberParams> groupChatMemberParamsListFrom(List<GroupMember> members) {
        return CollectionUtils.emptyIfNull(members).stream()
                .map(this::groupChatMemberParamsFrom)
                .toList();
    }

    @Mapping(target = "groupId", source = "groupId.value")
    @Mapping(target = "userId", source = "userId.value")
    @Mapping(target = "joinTime", source = "joinTime")
    GroupChatMemberParams groupChatMemberParamsFrom(GroupMember member);

    default List<GroupChatMemberDescriptorParams> groupChatMemberDescriptorParamsListFrom(
            List<MemberDescriptor> descriptors) {
        return CollectionUtils.emptyIfNull(descriptors).stream()
                .map(this::groupChatMemberDescriptorParamsFrom)
                .toList();
    }

    @Mapping(target = "userId", source = "userId.value")
    @Mapping(target = "displayName", source = "displayName.value")
    @Mapping(target = "joinTime", source = "joinTime")
    GroupChatMemberDescriptorParams groupChatMemberDescriptorParamsFrom(MemberDescriptor descriptor);

    UserGroupChatSummary userGroupChatSummaryFrom(UserGroupChatSummaryDTO response);
}
