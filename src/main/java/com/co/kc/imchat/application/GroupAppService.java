package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.group.ImGroup;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.group.ImUserGroupDescriptor;
import com.co.kc.imchat.domain.group.ImGroupId;
import com.co.kc.imchat.domain.group.ImGroupMember;
import com.co.kc.imchat.domain.group.ImGroupMemberRepository;
import com.co.kc.imchat.domain.group.ImGroupName;
import com.co.kc.imchat.domain.group.ImGroupRepository;
import com.co.kc.imchat.domain.group.ImGroupRoster;
import com.co.kc.imchat.domain.group.ImGroupService;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupCreateCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupInviteMembersCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupCreateDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupDetailDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupItemDTO;
import com.co.kc.imchat.model.cqrs.query.ImGroupDetailQuery;
import com.co.kc.imchat.model.cqrs.query.ImGroupListQuery;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.application.GroupAppTransformer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class GroupAppService {
    private final SnowflakeId snowflakeId;
    private final ImGroupRepository imGroupRepository;
    private final ImGroupChatRepository imGroupChatRepository;
    private final ImGroupMemberRepository imGroupMemberRepository;
    private final ImGroupService imGroupService;
    private final ImChatService imChatService;

    @Transactional(rollbackFor = Exception.class)
    public ImGroupCreateDTO createGroup(ImGroupCreateCmd command) {
        UserId ownerId = new UserId(command.getOwnerId());
        ImGroupId groupId = new ImGroupId(snowflakeId.next());
        ImGroupName groupName = new ImGroupName(command.getGroupName());
        List<UserId> memberIds = FunctionUtils.mappingList(command.getMemberIds(), UserId::new);

        ImGroup imGroup = ImGroup.builder()
                .id(groupId)
                .type(ImChatType.GROUP)
                .ownerId(ownerId)
                .name(groupName)
                .build();
        imGroupRepository.save(imGroup);

        ImGroupRoster groupRoster = imGroupService.createRoster(groupId, ownerId, memberIds);
        imGroupMemberRepository.saveAll(groupRoster.getMembers());

        List<ImGroupChat> groupChats = imChatService.createGroupChats(groupRoster.getMembers());
        imGroupChatRepository.saveAll(groupChats);

        ImGroupChat ownerChat = groupChats.stream()
                .filter(groupChat -> groupChat.getUserId().equals(ownerId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("群主会话创建失败"));
        imChatService.enterChat(ownerChat);

        return new ImGroupCreateDTO(groupId.getValue(), ownerChat.getId().getValue());
    }

    @Transactional(rollbackFor = Exception.class)
    public void inviteGroupMembers(ImGroupInviteMembersCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImGroupId groupId = new ImGroupId(command.getGroupId());
        List<UserId> inviteeIds = FunctionUtils.mappingList(command.getInviteeIds(), UserId::new);

        ImGroup group = imGroupRepository.find(groupId);
        if (group == null) {
            throw new NotFoundException("群组不存在");
        }
        ImGroupRoster groupRoster = imGroupService.findGroup(group.getId());
        List<ImGroupMember> newInvitees = groupRoster.invite(userId, inviteeIds);
        imGroupMemberRepository.saveAll(newInvitees);

        List<ImGroupChat> newMemberChatList = imChatService.createGroupChats(newInvitees);
        imGroupChatRepository.saveAll(newMemberChatList);
    }

    public List<ImGroupItemDTO> getGroupList(ImGroupListQuery query) {
        UserId userId = new UserId(query.getUserId());

        List<ImGroup> groups = imGroupRepository.find(userId);
        if (CollectionUtils.isEmpty(groups)) {
            return Collections.emptyList();
        }

        List<ImUserGroupDescriptor> groupDescriptors = imGroupService.describeUserGroups(userId, groups);
        if (CollectionUtils.isEmpty(groupDescriptors)) {
            return Collections.emptyList();
        }

        return GroupAppTransformer.INSTANCE.imGroupDtoListFrom(groupDescriptors);
    }

    public ImGroupDetailDTO getGroupDetail(ImGroupDetailQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImGroupId groupId = new ImGroupId(query.getGroupId());
        ImGroup group = imGroupRepository.find(groupId);
        if (group == null) {
            throw new NotFoundException("群组不存在");
        }
        ImGroupChat groupChat = imGroupChatRepository.find(groupId, userId);
        if (groupChat == null || imGroupMemberRepository.find(groupId, userId) == null) {
            throw new BusinessException("用户无此群组权限");
        }
        List<ImGroupMember> members = imGroupMemberRepository.find(groupId);
        return GroupAppTransformer.INSTANCE.imGroupDetailDtoFrom(group, groupChat, members);
    }
}
