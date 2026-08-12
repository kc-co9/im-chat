package com.co.kc.imchat.service.social.application;

import com.co.kc.imchat.common.exception.BusinessException;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.service.social.domain.group.model.Group;
import com.co.kc.imchat.service.social.domain.group.model.GroupCreation;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.service.social.domain.group.model.GroupMemberDeparture;
import com.co.kc.imchat.service.social.domain.group.model.GroupMemberInvitation;
import com.co.kc.imchat.service.social.domain.group.model.GroupName;
import com.co.kc.imchat.service.social.domain.group.model.GroupNotification;
import com.co.kc.imchat.common.domain.group.model.GroupUserAlias;
import com.co.kc.imchat.common.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import com.co.kc.imchat.service.social.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.service.social.domain.group.repository.GroupRepository;
import com.co.kc.imchat.service.social.domain.group.service.GroupService;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.social.model.cqrs.command.group.GroupCreateCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.group.GroupDismissCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.group.GroupInviteMembersCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.group.GroupKickMemberCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.group.GroupLeaveCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.group.GroupMemberAliasChangeCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.group.GroupNotificationChangeCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.group.GroupTransferOwnerCmd;
import com.co.kc.imchat.service.social.facade.dto.GroupMemberCheckDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMemberItemDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMembersDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMessageRecipientDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMessageRecipientsDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupSummariesDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupSummaryDTO;
import com.co.kc.imchat.service.social.facade.params.GroupMemberCheckParams;
import com.co.kc.imchat.service.social.facade.params.GroupMembersGetParams;
import com.co.kc.imchat.service.social.facade.params.GroupMessageRecipientsGetParams;
import com.co.kc.imchat.service.social.facade.params.GroupSummariesGetParams;
import com.co.kc.imchat.service.social.model.cqrs.dto.group.GroupCreateDTO;
import com.co.kc.imchat.service.social.model.cqrs.dto.group.GroupDetailDTO;
import com.co.kc.imchat.service.social.model.cqrs.dto.group.GroupItemDTO;
import com.co.kc.imchat.service.social.model.cqrs.query.group.GroupDetailQuery;
import com.co.kc.imchat.service.social.model.cqrs.query.group.GroupListQuery;
import com.co.kc.imchat.service.social.transformer.GroupAppTransformer;
import com.co.kc.imchat.service.social.adapter.account.AccountAdapter;
import com.co.kc.imchat.service.social.adapter.message.MessageSocialAdapter;
import com.co.kc.imchat.service.social.domain.account.model.UserProfile;
import com.co.kc.imchat.service.social.domain.message.model.UserGroupChatSummary;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GroupAppService {
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final FriendRepository friendRepository;
    private final GroupService groupService;
    private final AccountAdapter accountAdapter;
    private final MessageSocialAdapter messageSocialAdapter;
    private final SnowflakeId snowflakeId;
    private final AfterTransactionCommitTemplate afterTransactionCommitTemplate;

    public GroupAppService(GroupRepository groupRepository,
                           GroupMemberRepository groupMemberRepository,
                           FriendRepository friendRepository,
                           GroupService groupService,
                           AccountAdapter accountAdapter,
                           MessageSocialAdapter messageSocialAdapter,
                           SnowflakeId snowflakeId,
                           AfterTransactionCommitTemplate afterTransactionCommitTemplate) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.friendRepository = friendRepository;
        this.groupService = groupService;
        this.accountAdapter = accountAdapter;
        this.messageSocialAdapter = messageSocialAdapter;
        this.snowflakeId = snowflakeId;
        this.afterTransactionCommitTemplate = afterTransactionCommitTemplate;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public GroupCreateDTO createGroup(GroupCreateCmd command) {
        UserId ownerId = new UserId(command.ownerId());
        GroupId groupId = new GroupId(snowflakeId.next());
        GroupName groupName = new GroupName(command.groupName());
        List<UserId> memberIds = FunctionUtils.mappingList(command.memberIds(), UserId::new);

        GroupCreation groupCreation = groupService.createGroup(groupId, ownerId, groupName, memberIds);
        groupRepository.save(groupCreation.group());
        groupMemberRepository.save(groupCreation.members());

        afterTransactionCommitTemplate.execute(() ->
                messageSocialAdapter.onGroupCreated(groupId.value(), ownerId.value(), groupCreation.members()));
        return GroupAppTransformer.INSTANCE.groupCreateDtoFrom(groupId);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void dismissGroup(GroupDismissCmd command) {
        UserId userId = new UserId(command.userId());
        GroupId groupId = new GroupId(command.groupId());

        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));
        group.dismiss(userId);
        groupRepository.save(group);

        afterTransactionCommitTemplate.execute(() ->
                messageSocialAdapter.onGroupDismissed(groupId.value(), userId.value()));
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void inviteGroupMembers(GroupInviteMembersCmd command) {
        UserId userId = new UserId(command.userId());
        GroupId groupId = new GroupId(command.groupId());
        List<UserId> inviteeIds = FunctionUtils.mappingList(command.inviteeIds(), UserId::new);

        GroupMemberInvitation invitation = groupService.inviteMembers(groupId, userId, inviteeIds);
        groupRepository.save(invitation.group());
        groupMemberRepository.save(invitation.newMembers());

        List<MemberDescriptor> descriptors = describeGroupMembers(userId, invitation.newMembers());
        afterTransactionCommitTemplate.execute(() -> messageSocialAdapter.onGroupMemberJoined(
                userId.value(),
                groupId.value(),
                invitation.newMembers(),
                descriptors));
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void transferGroupOwner(GroupTransferOwnerCmd command) {
        UserId userId = new UserId(command.userId());
        GroupId groupId = new GroupId(command.groupId());
        UserId newOwnerId = new UserId(command.newOwnerId());

        Group group = groupService.transferOwner(groupId, userId, newOwnerId);
        groupRepository.save(group);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void leaveGroup(GroupLeaveCmd command) {
        UserId userId = new UserId(command.userId());
        GroupId groupId = new GroupId(command.groupId());

        GroupMemberDeparture departure = groupService.leaveGroup(groupId, userId);
        groupRepository.save(departure.group());
        groupMemberRepository.remove(departure.groupMember());

        afterTransactionCommitTemplate.execute(() ->
                messageSocialAdapter.onGroupMemberRemoved(groupId.value(), departure.groupMember().getUserId().value()));
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void kickGroupMember(GroupKickMemberCmd command) {
        UserId userId = new UserId(command.userId());
        GroupId groupId = new GroupId(command.groupId());
        UserId memberId = new UserId(command.memberUserId());

        GroupMemberDeparture departure = groupService.kickMember(groupId, userId, memberId);
        groupRepository.save(departure.group());
        groupMemberRepository.remove(departure.groupMember());

        afterTransactionCommitTemplate.execute(() ->
                messageSocialAdapter.onGroupMemberRemoved(groupId.value(), departure.groupMember().getUserId().value()));
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void changeGroupNotification(GroupNotificationChangeCmd command) {
        UserId userId = new UserId(command.userId());
        GroupId groupId = new GroupId(command.groupId());
        GroupNotification notification = new GroupNotification(command.notification());

        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));
        group.changeNotification(userId, notification);
        groupRepository.save(group);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void changeGroupMemberAlias(GroupMemberAliasChangeCmd command) {
        UserId userId = new UserId(command.userId());
        GroupId groupId = new GroupId(command.groupId());

        GroupMember member = groupService.changeMemberAlias(groupId, userId, new GroupUserAlias(command.userAlias()));
        groupMemberRepository.save(member);
    }

    public List<GroupItemDTO> getGroupList(GroupListQuery query) {
        UserId userId = new UserId(query.userId());

        List<Group> groups = groupRepository.find(userId).stream()
                .filter(group -> !group.isDismissed())
                .toList();
        if (CollectionUtils.isEmpty(groups)) {
            return Collections.emptyList();
        }

        Set<Long> groupIds = groups.stream()
                .map(group -> group.getId().value())
                .collect(Collectors.toSet());
        Map<Long, UserGroupChatSummary> chatMap = messageSocialAdapter.getUserGroupChatSummaries(userId.value(), groupIds)
                .stream()
                .collect(Collectors.toMap(UserGroupChatSummary::groupId, Function.identity(), (left, right) -> left));

        return groups.stream()
                .map(group -> GroupAppTransformer.INSTANCE.groupItemDtoFrom(group, chatMap.get(group.getId().value())))
                .filter(item -> item.getChatId() != null)
                .toList();
    }

    public GroupDetailDTO getGroupDetail(GroupDetailQuery query) {
        UserId userId = new UserId(query.userId());
        GroupId groupId = new GroupId(query.groupId());

        Group group = groupRepository.find(groupId).orElseThrow(() -> new NotFoundException("群组不存在"));
        groupService.ensureGroupMember(group, userId);

        UserGroupChatSummary groupChat = messageSocialAdapter.getUserGroupChatSummary(userId.value(), groupId.value())
                .orElseThrow(() -> new BusinessException("用户无此群组权限"));

        List<GroupMember> members = groupMemberRepository.find(groupId).stream()
                .sorted(Comparator.comparing(member -> !member.getUserId().equals(group.getOwnerId())))
                .toList();
        List<MemberDescriptor> memberDescriptors = describeGroupMembers(userId, members);

        return GroupAppTransformer.INSTANCE.groupDetailDtoFrom(group, groupChat, memberDescriptors);
    }

    public GroupMemberCheckDTO checkGroupMember(GroupMemberCheckParams params) {
        Group group = getActiveGroup(params.groupId());
        boolean member = groupMemberRepository.contain(group.getId(), new UserId(params.userId()));
        return GroupAppTransformer.INSTANCE.groupMemberCheckDtoFrom(member);
    }

    public GroupMembersDTO getGroupMembers(GroupMembersGetParams params) {
        Group group = getActiveGroup(params.groupId());
        List<GroupMemberItemDTO> members = groupMemberRepository.find(group.getId()).stream()
                .map(GroupAppTransformer.INSTANCE::groupMemberItemDtoFrom)
                .toList();
        return GroupAppTransformer.INSTANCE.groupMembersDtoFrom(group.getId(), members);
    }

    public GroupMessageRecipientsDTO getGroupMessageRecipients(GroupMessageRecipientsGetParams params) {
        Group group = getActiveGroup(params.groupId());
        List<GroupMessageRecipientDTO> recipients = groupMemberRepository.find(group.getId()).stream()
                .map(GroupAppTransformer.INSTANCE::groupMessageRecipientDtoFrom)
                .toList();
        return GroupAppTransformer.INSTANCE.groupMessageRecipientsDtoFrom(group.getId(), recipients);
    }

    public GroupSummariesDTO getGroupSummaries(GroupSummariesGetParams params) {
        List<GroupId> groupIds = FunctionUtils.mappingList(params.groupIds(), GroupId::new);
        List<GroupSummaryDTO> groups = groupRepository.find(groupIds).stream()
                .map(GroupAppTransformer.INSTANCE::groupSummaryDtoFrom)
                .toList();
        return GroupAppTransformer.INSTANCE.groupSummariesDtoFrom(groups);
    }

    private Group getActiveGroup(Long groupId) {
        Group group = groupRepository.find(new GroupId(groupId))
                .orElseThrow(() -> new NotFoundException("群组不存在"));
        group.ensureActive();
        return group;
    }

    private List<MemberDescriptor> describeGroupMembers(UserId viewerId, List<GroupMember> members) {
        if (viewerId == null || CollectionUtils.isEmpty(members)) {
            return Collections.emptyList();
        }

        List<UserId> memberIds = FunctionUtils.mappingList(members, GroupMember::getUserId);
        Map<UserId, Friend> friendMap = FunctionUtils.mappingMap(
                friendRepository.find(viewerId, memberIds),
                Friend::getFriendUserId,
                Function.identity());
        Map<UserId, UserProfile> profileMap = members.stream()
                .filter(member -> friendMap.get(member.getUserId()) == null)
                .filter(member -> member.getUserAlias() == null)
                .map(GroupMember::getUserId)
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        userId -> accountAdapter.getUserProfile(userId.value()),
                        (left, right) -> left));
        return groupService.describeMembers(members, friendMap, profileMap);
    }
}
