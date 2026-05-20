package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.GroupChatMembership;
import com.co.kc.imchat.domain.chat.GroupChatJoin;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.group.GroupCreatedEvent;
import com.co.kc.imchat.domain.group.Group;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.group.UserGroupDescriptor;
import com.co.kc.imchat.domain.group.GroupCreation;
import com.co.kc.imchat.domain.group.GroupDismissedEvent;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.group.GroupMember;
import com.co.kc.imchat.domain.group.GroupMemberInvitation;
import com.co.kc.imchat.domain.group.GroupMemberJoinedEvent;
import com.co.kc.imchat.domain.group.MemberDescriptor;
import com.co.kc.imchat.domain.group.GroupMemberRemovedEvent;
import com.co.kc.imchat.domain.group.GroupMemberRepository;
import com.co.kc.imchat.domain.group.GroupMemberDeparture;
import com.co.kc.imchat.domain.group.GroupName;
import com.co.kc.imchat.domain.group.GroupNotification;
import com.co.kc.imchat.domain.group.GroupRepository;
import com.co.kc.imchat.domain.group.GroupService;
import com.co.kc.imchat.domain.group.GroupUserAlias;
import com.co.kc.imchat.domain.message.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImGroupMessageSentEvent;
import com.co.kc.imchat.domain.message.ImGroupMessageTransmission;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.command.group.GroupCreateCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupDismissCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupInviteMembersCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupKickMemberCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupLeaveCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupMemberAliasChangeCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupNotificationChangeCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupTransferOwnerCmd;
import com.co.kc.imchat.model.cqrs.dto.group.GroupCreateDTO;
import com.co.kc.imchat.model.cqrs.dto.group.GroupDetailDTO;
import com.co.kc.imchat.model.cqrs.dto.group.GroupItemDTO;
import com.co.kc.imchat.model.cqrs.query.group.GroupDetailQuery;
import com.co.kc.imchat.model.cqrs.query.group.GroupListQuery;
import com.co.kc.imchat.support.event.DomainEventPublisher;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.support.lock.DistributeLockScene;
import com.co.kc.imchat.support.lock.annotation.DistributeLock;
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
    private final GroupRepository groupRepository;
    private final ImGroupChatRepository imGroupChatRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;

    private final GroupService groupService;
    private final ImChatService imChatService;
    private final ImMessageService imMessageService;

    private final SnowflakeId snowflakeId;
    private final DomainEventPublisher domainEventPublisher;

    @DistributeLock(scene = DistributeLockScene.GROUP_CREATE, key = "#command.ownerId + ':' + #command.groupName")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public GroupCreateDTO createGroup(GroupCreateCmd command) {
        UserId ownerId = new UserId(command.getOwnerId());
        GroupId groupId = new GroupId(snowflakeId.next());
        GroupName groupName = new GroupName(command.getGroupName());
        List<UserId> memberIds = FunctionUtils.mappingList(command.getMemberIds(), UserId::new);

        GroupCreation groupCreation = groupService.createGroup(groupId, ownerId, groupName, memberIds);
        groupRepository.save(groupCreation.getGroup());
        groupMemberRepository.save(groupCreation.getMembers());

        domainEventPublisher.publish(new GroupCreatedEvent(groupId, ownerId, groupCreation.getMembers()));

        return new GroupCreateDTO(groupId.getValue());
    }

    public void onGroupCreated(GroupCreatedEvent event) {
        GroupChatMembership chatMembership = imChatService.createGroupChatMembership(event.getMembers());

        ImGroupMessageTransmission transmission = imMessageService.transmitGroupCreated(event.getOwnerId(), chatMembership);
        imGroupInboxMessageRepository.save(transmission.getInboxMessages());
        imGroupChatRepository.save(transmission.getGroupChats());

        ImGroupMessageSentEvent imGroupMessageSentEvent =
                imMessageService.newImMessageSentEvent(event.getGroupId(), transmission.getSenderMessage(event.getOwnerId()));
        domainEventPublisher.publish(imGroupMessageSentEvent);
    }

    @DistributeLock(scene = DistributeLockScene.GROUP_DISMISS, key = "#command.groupId")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void dismissGroup(GroupDismissCmd command) {
        UserId userId = new UserId(command.getUserId());
        GroupId groupId = new GroupId(command.getGroupId());

        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));
        group.dismiss(userId);
        groupRepository.save(group);

        domainEventPublisher.publish(new GroupDismissedEvent(groupId, userId));
    }

    public void onGroupDismissed(GroupDismissedEvent event) {
        GroupChatMembership chatMembership = imChatService.findGroupChatMembership(event.getGroupId());

        ImGroupMessageTransmission transmission = imMessageService.transmitGroupDismissed(event.getOwnerId(), chatMembership);
        imGroupInboxMessageRepository.save(transmission.getInboxMessages());
        imGroupChatRepository.save(transmission.getGroupChats());

        ImGroupMessageSentEvent messageSentEvent =
                imMessageService.newImMessageSentEvent(event.getGroupId(), transmission.getSenderMessage(event.getOwnerId()));
        domainEventPublisher.publish(messageSentEvent);
    }

    @DistributeLock(scene = DistributeLockScene.GROUP_MEMBER_INVITE, key = "#command.groupId")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void inviteGroupMembers(GroupInviteMembersCmd command) {
        UserId userId = new UserId(command.getUserId());
        GroupId groupId = new GroupId(command.getGroupId());
        List<UserId> inviteeIds = FunctionUtils.mappingList(command.getInviteeIds(), UserId::new);

        GroupMemberInvitation invitation = groupService.inviteMembers(groupId, userId, inviteeIds);

        groupRepository.save(invitation.getGroup());
        groupMemberRepository.save(invitation.getNewMembers());

        domainEventPublisher.publish(new GroupMemberJoinedEvent(userId, groupId, invitation.getNewMembers()));
    }

    public void onGroupMemberJoined(GroupMemberJoinedEvent event) {
        GroupChatJoin chatJoin = imChatService.joinGroupChat(event.getGroupId(), event.getMembers());

        List<MemberDescriptor> memberDescriptors = groupService.describeGroupMembers(event.getInviterId(), event.getMembers());

        ImGroupMessageTransmission transmission =
                imMessageService.transmitGroupMemberJoined(event.getInviterId(), chatJoin.describeChatMembership(), memberDescriptors);
        imGroupInboxMessageRepository.save(transmission.getInboxMessages());
        imGroupChatRepository.save(transmission.getGroupChats());

        ImGroupMessageSentEvent messageSentEvent =
                imMessageService.newImMessageSentEvent(event.getGroupId(), transmission.getSenderMessage(event.getInviterId()));
        domainEventPublisher.publish(messageSentEvent);
    }

    @DistributeLock(scene = DistributeLockScene.GROUP_OWNER_TRANSFER, key = "#command.groupId")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void transferGroupOwner(GroupTransferOwnerCmd command) {
        UserId userId = new UserId(command.getUserId());
        GroupId groupId = new GroupId(command.getGroupId());
        UserId newOwnerId = new UserId(command.getNewOwnerId());

        Group group = groupService.transferOwner(groupId, userId, newOwnerId);
        groupRepository.save(group);
    }

    @DistributeLock(scene = DistributeLockScene.GROUP_MEMBER_LEAVE, key = "#command.groupId")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void leaveGroup(GroupLeaveCmd command) {
        UserId userId = new UserId(command.getUserId());
        GroupId groupId = new GroupId(command.getGroupId());

        GroupMemberDeparture departure = groupService.leaveGroup(groupId, userId);
        groupRepository.save(departure.getGroup());
        groupMemberRepository.remove(departure.getGroupMember());

        domainEventPublisher.publish(new GroupMemberRemovedEvent(groupId, departure.getGroupMember().getUserId()));
    }

    @DistributeLock(scene = DistributeLockScene.GROUP_MEMBER_KICK, key = "#command.groupId")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void kickGroupMember(GroupKickMemberCmd command) {
        UserId userId = new UserId(command.getUserId());
        GroupId groupId = new GroupId(command.getGroupId());
        UserId memberId = new UserId(command.getMemberUserId());

        GroupMemberDeparture departure = groupService.kickMember(groupId, userId, memberId);
        groupRepository.save(departure.getGroup());
        groupMemberRepository.remove(departure.getGroupMember());

        domainEventPublisher.publish(new GroupMemberRemovedEvent(groupId, departure.getGroupMember().getUserId()));
    }

    public void onGroupMemberRemoved(GroupMemberRemovedEvent event) {
        imGroupChatRepository.remove(event.getGroupId(), event.getUserId());
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void changeGroupNotification(GroupNotificationChangeCmd command) {
        UserId userId = new UserId(command.getUserId());
        GroupId groupId = new GroupId(command.getGroupId());
        GroupNotification notification = new GroupNotification(command.getNotification());

        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));
        group.changeNotification(userId, notification);
        groupRepository.save(group);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void changeGroupMemberAlias(GroupMemberAliasChangeCmd command) {
        UserId userId = new UserId(command.getUserId());
        GroupId groupId = new GroupId(command.getGroupId());

        GroupMember member = groupService.changeMemberAlias(groupId, userId, new GroupUserAlias(command.getUserAlias()));
        groupMemberRepository.save(member);
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

        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));
        groupService.ensureGroupMember(group, userId);

        ImGroupChat groupChat = imGroupChatRepository.find(groupId, userId).orElseThrow(() -> new BusinessException("用户无此群组权限"));

        List<GroupMember> members = groupMemberRepository.find(groupId).stream()
                .sorted(Comparator.comparing(member -> !member.getUserId().equals(group.getOwnerId())))
                .collect(Collectors.toList());
        List<MemberDescriptor> memberDescriptors = groupService.describeGroupMembers(userId, members);

        return GroupAppTransformer.INSTANCE.groupDetailDtoFrom(group, groupChat, memberDescriptors);
    }
}
