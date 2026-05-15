package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.group.Group;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.group.UserGroupDescriptor;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.group.GroupMember;
import com.co.kc.imchat.domain.group.MemberDescriptor;
import com.co.kc.imchat.domain.group.GroupMemberRepository;
import com.co.kc.imchat.domain.group.GroupName;
import com.co.kc.imchat.domain.group.GroupRepository;
import com.co.kc.imchat.domain.group.GroupRoster;
import com.co.kc.imchat.domain.group.GroupService;
import com.co.kc.imchat.domain.message.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImGroupMessageSentEvent;
import com.co.kc.imchat.domain.message.ImGroupMessageTransmission;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.command.group.GroupCreateCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupDismissCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupInviteMembersCmd;
import com.co.kc.imchat.model.cqrs.dto.group.GroupCreateDTO;
import com.co.kc.imchat.model.cqrs.dto.group.GroupDetailDTO;
import com.co.kc.imchat.model.cqrs.dto.group.GroupItemDTO;
import com.co.kc.imchat.model.cqrs.query.group.GroupDetailQuery;
import com.co.kc.imchat.model.cqrs.query.group.GroupListQuery;
import com.co.kc.imchat.support.event.DomainEventPublisher;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.application.GroupAppTransformer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class GroupAppService {
    private final SnowflakeId snowflakeId;
    private final GroupRepository groupRepository;
    private final ImGroupChatRepository imGroupChatRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupService groupService;
    private final ImChatService imChatService;
    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;
    private final ImMessageService imMessageService;
    private final DomainEventPublisher imMessageEventPublisher;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public GroupCreateDTO createGroup(GroupCreateCmd command) {
        UserId ownerId = new UserId(command.getOwnerId());
        GroupId groupId = new GroupId(snowflakeId.next());
        GroupName groupName = new GroupName(command.getGroupName());
        List<UserId> memberIds = FunctionUtils.mappingList(command.getMemberIds(), UserId::new);

        Group imGroup = Group.builder()
                .id(groupId)
                .type(ImChatType.GROUP)
                .ownerId(ownerId)
                .name(groupName)
                .build();
        groupRepository.save(imGroup);

        GroupRoster groupRoster = groupService.createRoster(groupId, ownerId, memberIds);
        groupMemberRepository.saveAll(groupRoster.getMembers());

        List<ImGroupChat> groupChats = imChatService.createGroupChats(groupRoster.getMembers());
        ImGroupChat ownerChat = groupChats.stream()
                .filter(groupChat -> groupChat.belongsTo(ownerId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("群主会话创建失败"));
        imChatService.enterChat(ownerChat);

        ImGroupMessageTransmission transmission =
                imMessageService.transmitGroupCreated(groupId, ownerId, ownerChat, groupChats);
        imGroupInboxMessageRepository.saveAll(transmission.getInboxMessages());
        imGroupChatRepository.saveAll(transmission.getGroupChats());

        ImGroupMessageSentEvent imGroupMessageSentEvent =
                imMessageService.newImMessageSentEvent(groupId, transmission.getSenderMessage(ownerId));
        imMessageEventPublisher.publish(imGroupMessageSentEvent);

        return new GroupCreateDTO(groupId.getValue(), ownerChat.getId().getValue());
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void dismissGroup(GroupDismissCmd command) {
        UserId userId = new UserId(command.getUserId());
        GroupId groupId = new GroupId(command.getGroupId());

        Group group = groupRepository.find(groupId)
                .orElseThrow(() -> new NotFoundException("群组不存在"));
        group.dismiss(userId);
        groupRepository.save(group);

        List<ImGroupChat> groupChats = imGroupChatRepository.find(groupId);
        ImGroupChat ownerChat = groupChats.stream()
                .filter(groupChat -> groupChat.belongsTo(userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("群主会话不存在"));
        ImGroupMessageTransmission transmission =
                imMessageService.transmitGroupDismissed(groupId, userId, ownerChat, groupChats);
        imGroupInboxMessageRepository.saveAll(transmission.getInboxMessages());
        imGroupChatRepository.saveAll(transmission.getGroupChats());

        ImGroupMessageSentEvent event =
                imMessageService.newImMessageSentEvent(groupId, transmission.getSenderMessage(userId));
        imMessageEventPublisher.publish(event);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void inviteGroupMembers(GroupInviteMembersCmd command) {
        UserId userId = new UserId(command.getUserId());
        GroupId groupId = new GroupId(command.getGroupId());
        List<UserId> inviteeIds = FunctionUtils.mappingList(command.getInviteeIds(), UserId::new);

        Group group = groupRepository.find(groupId)
                .orElseThrow(() -> new NotFoundException("群组不存在"));
        group.ensureActive();

        GroupRoster groupRoster = groupService.findGroup(group.getId());
        List<GroupMember> newInvitees = groupRoster.invite(userId, inviteeIds);
        groupMemberRepository.saveAll(newInvitees);

        List<ImGroupChat> newMemberChatList = imChatService.createGroupChats(newInvitees);
        imGroupChatRepository.saveAll(newMemberChatList);
    }

    public List<GroupItemDTO> getGroupList(GroupListQuery query) {
        UserId userId = new UserId(query.getUserId());

        List<Group> groups = groupRepository.find(userId);
        if (CollectionUtils.isEmpty(groups)) {
            return Collections.emptyList();
        }
        groups = groups.stream()
                .filter(group -> !group.isDismissed())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(groups)) {
            return Collections.emptyList();
        }

        List<UserGroupDescriptor> groupDescriptors = groupService.describeUserGroups(userId, groups);
        if (CollectionUtils.isEmpty(groupDescriptors)) {
            return Collections.emptyList();
        }

        return GroupAppTransformer.INSTANCE.groupDtoListFrom(groupDescriptors);
    }

    public GroupDetailDTO getGroupDetail(GroupDetailQuery query) {
        UserId userId = new UserId(query.getUserId());
        GroupId groupId = new GroupId(query.getGroupId());

        Group group = groupRepository.find(groupId)
                .orElseThrow(() -> new NotFoundException("群组不存在"));
        group.ensureActive();

        ImGroupChat groupChat = imGroupChatRepository.find(groupId, userId)
                .orElseThrow(() -> new BusinessException("用户无此群组权限"));
        if (!groupMemberRepository.contain(groupId, userId)) {
            throw new BusinessException("用户无此群组权限");
        }
        List<GroupMember> members = groupMemberRepository.find(groupId).stream()
                .sorted(Comparator.comparing(member -> !member.getUserId().equals(group.getOwnerId())))
                .collect(Collectors.toList());
        List<MemberDescriptor> memberDescriptors = groupService.describeGroupMembers(userId, members);
        return GroupAppTransformer.INSTANCE.groupDetailDtoFrom(group, groupChat, memberDescriptors);
    }
}
