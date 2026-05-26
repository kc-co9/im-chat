package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.chat.service.ImChatService;
import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.domain.group.event.GroupCreatedEvent;
import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.model.FriendAlias;
import com.co.kc.imchat.domain.friend.model.FriendEdge;
import com.co.kc.imchat.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.domain.group.event.GroupDismissedEvent;
import com.co.kc.imchat.domain.group.event.GroupMemberJoinedEvent;
import com.co.kc.imchat.domain.group.event.GroupMemberRemovedEvent;
import com.co.kc.imchat.domain.group.model.GroupName;
import com.co.kc.imchat.domain.group.model.GroupNotification;
import com.co.kc.imchat.domain.group.repository.GroupRepository;
import com.co.kc.imchat.domain.group.service.GroupService;
import com.co.kc.imchat.domain.group.model.GroupStatus;
import com.co.kc.imchat.domain.group.model.GroupUserAlias;
import com.co.kc.imchat.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.domain.group.model.MemberCount;
import com.co.kc.imchat.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.event.ImGroupMessageSentEvent;
import com.co.kc.imchat.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.model.ImMessage;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.message.service.ImMessageService;
import com.co.kc.imchat.domain.message.model.ImMessageToken;
import com.co.kc.imchat.domain.message.model.ImMessageType;
import com.co.kc.imchat.domain.group.model.MemberId;
import com.co.kc.imchat.domain.session.model.Session;
import com.co.kc.imchat.domain.shared.event.DomainEvent;
import com.co.kc.imchat.domain.session.repository.SessionRepository;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
import com.co.kc.imchat.domain.user.repository.UserRepository;
import com.co.kc.imchat.domain.user.model.UserEmail;
import com.co.kc.imchat.common.identity.snowflake.ISnowflakeMachineId;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupCreateCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupDismissCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupInviteMembersCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupKickMemberCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupLeaveCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMemberAliasChangeCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupNotificationChangeCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupTransferOwnerCmd;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupCreateDTO;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupDetailDTO;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupItemDTO;
import com.co.kc.imchat.application.model.cqrs.query.group.GroupDetailQuery;
import com.co.kc.imchat.application.model.cqrs.query.group.GroupListQuery;
import com.co.kc.imchat.application.support.event.DomainEventPublisher;
import com.co.kc.imchat.common.exception.BusinessException;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.application.support.lock.DistributeLockScene;
import com.co.kc.imchat.application.support.lock.annotation.DistributeLock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupAppServiceTest {

    @Test
    void groupMemberMutationMethodsShareGroupIdLock() throws NoSuchMethodException {
        assertGroupMemberLock(
                GroupAppService.class.getMethod("inviteGroupMembers", GroupInviteMembersCmd.class),
                DistributeLockScene.GROUP_MEMBER_INVITE);
        assertGroupMemberLock(
                GroupAppService.class.getMethod("transferGroupOwner", GroupTransferOwnerCmd.class),
                DistributeLockScene.GROUP_OWNER_TRANSFER);
        assertGroupMemberLock(
                GroupAppService.class.getMethod("leaveGroup", GroupLeaveCmd.class),
                DistributeLockScene.GROUP_MEMBER_LEAVE);
        assertGroupMemberLock(
                GroupAppService.class.getMethod("kickGroupMember", GroupKickMemberCmd.class),
                DistributeLockScene.GROUP_MEMBER_KICK);
    }

    @Test
    void createGroupPersistsGroupAndMembersThenPublishesEvent() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        MemoryGroupInboxMessageRepository groupInboxMessageRepository = new MemoryGroupInboxMessageRepository();
        FixedSnowflakeId snowflakeId = new FixedSnowflakeId(1000L);
        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        GroupAppService appService = new GroupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                groupInboxMessageRepository,
                new GroupService(groupMemberRepository, groupChatRepository, groupRepository, null, null),
                new ImChatService(null, null, null, null, null, snowflakeId),
                new ImMessageService(groupInboxMessageRepository, null, snowflakeId),
                snowflakeId,
                eventPublisher);
        GroupCreateCmd command = new GroupCreateCmd(1L, Collections.singletonList(2L), "group");

        GroupCreateDTO result = appService.createGroup(command);

        assertThat(result.getGroupId()).isEqualTo(1000L);
        assertThat(groupRepository.savedGroup.getId().value()).isEqualTo(1000L);

        assertThat(groupMemberRepository.savedMembers)
                .extracting(member -> member.getUserId().value())
                .containsExactly(1L, 2L);

        assertThat(groupChatRepository.savedGroupChats).isEmpty();
        assertThat(groupInboxMessageRepository.savedMessages).isEmpty();
        GroupCreatedEvent groupCreatedEvent = firstEvent(eventPublisher, GroupCreatedEvent.class);
        assertThat(groupCreatedEvent.getGroupId().value()).isEqualTo(1000L);
        assertThat(groupCreatedEvent.getOwnerId().value()).isEqualTo(1L);
        assertThat(groupCreatedEvent.getMembers())
                .extracting(member -> member.getUserId().value())
                .containsExactly(1L, 2L);
    }

    private void assertGroupMemberLock(Method method, DistributeLockScene scene) {
        DistributeLock lock = method.getAnnotation(DistributeLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.scene()).isEqualTo(scene);
        assertThat(lock.key()).isEqualTo("#command.groupId()");
    }

    private <T extends DomainEvent> T firstEvent(MemoryDomainEventPublisher eventPublisher, Class<T> eventType) {
        return eventPublisher.events.stream()
                .filter(eventType::isInstance)
                .map(eventType::cast)
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    @Test
    void onGroupCreatedCreatesChatsSystemMessageAndUpdatesChats() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        MemoryGroupInboxMessageRepository groupInboxMessageRepository = new MemoryGroupInboxMessageRepository();
        FixedSnowflakeId snowflakeId = new FixedSnowflakeId(1000L);
        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        GroupAppService appService = new GroupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                groupInboxMessageRepository,
                new GroupService(groupMemberRepository, groupChatRepository, groupRepository, null, null),
                new ImChatService(null, null, null, null, new SignedInSessionRepository(), snowflakeId),
                new ImMessageService(groupInboxMessageRepository, null, snowflakeId),
                snowflakeId,
                eventPublisher);
        appService.onGroupCreated(new GroupCreatedEvent(
                new GroupId(1000L),
                new UserId(1L),
                Arrays.asList(groupMember(1000L, 1L), groupMember(1000L, 2L))));

        assertThat(groupInboxMessageRepository.savedMessages).hasSize(2);
        assertThat(groupInboxMessageRepository.savedMessages)
                .extracting(message -> message.getUserId().value())
                .containsExactly(1L, 2L);

        assertThat(groupInboxMessageRepository.savedMessages)
                .allSatisfy(message -> {
                    assertThat(message.getId().value()).isEqualTo(1002L);
                    assertThat(message.getSenderId().value()).isEqualTo(1L);
                    assertThat(message.getContent().type()).isEqualTo(ImMessageType.SYSTEM);
                    assertThat(message.getContent().value()).isEqualTo("群聊已创建");
                });
        assertThat(groupInboxMessageRepository.savedMessages)
                .extracting(ImGroupInboxMessage::getStatus)
                .containsExactly(ImGroupMessageStatus.READ, ImGroupMessageStatus.SENT);

        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getUserId().value())
                .containsExactly(1L, 2L);
        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getLastMessageId().value())
                .containsExactly(1002L, 1002L);
        assertThat(groupChatRepository.savedGroupChats)
                .extracting(ImGroupChat::getUnreadMessageCount)
                .containsExactly(0, 1);
        assertThat(eventPublisher.events)
                .singleElement()
                .isInstanceOfSatisfying(ImGroupMessageSentEvent.class, event -> {
                    assertThat(event.getGroupId()).isEqualTo(1000L);
                    assertThat(event.getSenderId()).isEqualTo(1L);
                    assertThat(event.getMessageContent()).isEqualTo("群聊已创建");
                });
    }

    @Test
    void inviteGroupMembersNotifiesAllMembersAfterCreatingNewMemberChats() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "group"));

        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));

        MemoryUserRepository userRepository = new MemoryUserRepository();
        userRepository.users.add(user(1L, "alice"));
        userRepository.users.add(user(2L, "bob"));

        MemoryGroupInboxMessageRepository groupInboxMessageRepository = new MemoryGroupInboxMessageRepository();
        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        GroupAppService appService = new GroupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                groupInboxMessageRepository,
                new GroupService(
                        groupMemberRepository, groupChatRepository, groupRepository,
                        new MemoryFriendRepository(), userRepository),
                new ImChatService(null, null, null, groupChatRepository, null, new FixedSnowflakeId(3000L)),
                new ImMessageService(groupInboxMessageRepository, null, new FixedSnowflakeId(4000L)),
                new FixedSnowflakeId(3000L),
                eventPublisher);
        GroupInviteMembersCmd command = new GroupInviteMembersCmd(1L, 1001L, Collections.singletonList(2L));

        appService.inviteGroupMembers(command);

        assertThat(groupRepository.savedGroup.getMemberCount().value()).isEqualTo(2);
        assertThat(groupMemberRepository.savedMembers)
                .extracting(member -> member.getUserId().value())
                .containsExactly(2L);

        assertThat(groupChatRepository.savedGroupChats).isEmpty();
        assertThat(eventPublisher.events)
                .singleElement()
                .isInstanceOfSatisfying(GroupMemberJoinedEvent.class, event -> {
                    assertThat(event.getInviterId().value()).isEqualTo(1L);
                    assertThat(event.getGroupId().value()).isEqualTo(1001L);
                    assertThat(event.getMembers())
                            .extracting(member -> member.getUserId().value())
                            .containsExactly(2L);
                });

        appService.onGroupMemberJoined((GroupMemberJoinedEvent) eventPublisher.events.getFirst());

        assertThat(groupInboxMessageRepository.savedMessages)
                .extracting(message -> message.getUserId().value())
                .containsExactly(1L, 2L);
        assertThat(groupInboxMessageRepository.savedMessages)
                .allSatisfy(message -> {
                    assertThat(message.getId().value()).isEqualTo(4000L);
                    assertThat(message.getSenderId().value()).isEqualTo(1L);
                    assertThat(message.getContent().type()).isEqualTo(ImMessageType.SYSTEM);
                    assertThat(message.getContent().value()).isEqualTo("bob 加入群聊");
                });

        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getUserId().value())
                .containsExactly(1L, 2L);
        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getLastMessageId().value())
                .containsExactly(4000L, 4000L);
        assertThat(groupChatRepository.savedGroupChats)
                .extracting(ImGroupChat::getUnreadMessageCount)
                .containsExactly(0, 1);
        assertThat(eventPublisher.events)
                .filteredOn(ImGroupMessageSentEvent.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(ImGroupMessageSentEvent.class, event -> {
                    assertThat(event.getGroupId()).isEqualTo(1001L);
                    assertThat(event.getSenderId()).isEqualTo(1L);
                    assertThat(event.getMessageContent()).isEqualTo("bob 加入群聊");
                });
    }

    @Test
    void inviteGroupMembersRejectsDismissedGroup() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(dismissedGroup(1001L, 1L, "group"));
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        GroupAppService appService = groupAppService(groupRepository, groupChatRepository, groupMemberRepository);

        GroupInviteMembersCmd command = new GroupInviteMembersCmd(1L, 1001L, Collections.singletonList(2L));

        assertThatThrownBy(() -> appService.inviteGroupMembers(command))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void dismissGroupRequiresOwner() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "group"));

        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();

        GroupAppService appService = groupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                new MemoryGroupInboxMessageRepository(),
                new FixedSnowflakeId(4000L));

        assertThatThrownBy(() -> appService.dismissGroup(new GroupDismissCmd(2L, 1001L)))
                .isInstanceOf(BusinessException.class);

        assertThat(groupRepository.savedGroup).isNull();
        assertThat(groupChatRepository.savedGroupChats).isEmpty();
    }

    @Test
    void dismissGroupMarksGroupDismissedAndPublishesEvent() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "group"));

        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));

        MemoryGroupInboxMessageRepository groupInboxMessageRepository = new MemoryGroupInboxMessageRepository();
        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        GroupAppService appService = groupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                groupInboxMessageRepository,
                new FixedSnowflakeId(4000L),
                eventPublisher);

        appService.dismissGroup(new GroupDismissCmd(1L, 1001L));

        assertThat(groupRepository.savedGroup.getStatus()).isEqualTo(GroupStatus.DISMISSED);
        assertThat(groupInboxMessageRepository.savedMessages).isEmpty();
        assertThat(groupChatRepository.savedGroupChats).isEmpty();
        assertThat(eventPublisher.events)
                .singleElement()
                .isInstanceOfSatisfying(GroupDismissedEvent.class, event -> {
                    assertThat(event.getGroupId().value()).isEqualTo(1001L);
                    assertThat(event.getOwnerId().value()).isEqualTo(1L);
                });
    }

    @Test
    void onGroupDismissedStoresSystemMessageAndUpdatesChats() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        MemoryGroupInboxMessageRepository groupInboxMessageRepository = new MemoryGroupInboxMessageRepository();
        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        GroupAppService appService = groupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                groupInboxMessageRepository,
                new FixedSnowflakeId(4000L),
                eventPublisher);

        appService.onGroupDismissed(new GroupDismissedEvent(new GroupId(1001L), new UserId(1L)));

        assertThat(groupInboxMessageRepository.savedMessages).hasSize(2);

        assertThat(groupInboxMessageRepository.savedMessages)
                .extracting(message -> message.getUserId().value())
                .containsExactly(1L, 2L);

        assertThat(groupInboxMessageRepository.savedMessages)
                .allSatisfy(message -> {
                    assertThat(message.getId().value()).isEqualTo(4000L);
                    assertThat(message.getSenderId().value()).isEqualTo(1L);
                    assertThat(message.getContent().type()).isEqualTo(ImMessageType.SYSTEM);
                    assertThat(message.getContent().value()).isEqualTo("群聊已解散");
                });
        assertThat(groupInboxMessageRepository.savedMessages)
                .extracting(ImGroupInboxMessage::getStatus)
                .containsExactly(ImGroupMessageStatus.READ, ImGroupMessageStatus.SENT);

        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getLastMessageId().value())
                .containsExactly(4000L, 4000L);
        assertThat(groupChatRepository.savedGroupChats)
                .extracting(ImGroupChat::getUnreadMessageCount)
                .containsExactly(0, 1);
        assertThat(eventPublisher.events)
                .singleElement()
                .isInstanceOfSatisfying(ImGroupMessageSentEvent.class, event -> {
                    assertThat(event.getGroupId()).isEqualTo(1001L);
                    assertThat(event.getSenderId()).isEqualTo(1L);
                    assertThat(event.getMessageContent()).isEqualTo("群聊已解散");
                });
    }

    @Test
    void getGroupListReturnsGroupsJoinedByUser() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(2)));
        groupRepository.groups.add(group(1002L, 2L, "beta"));

        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L, 901L, 2));
        groupChatRepository.groupChats.add(groupChat(102L, 1002L, 1L, null, 0));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        groupMemberRepository.members.add(groupMember(1002L, 1L));

        GroupAppService appService = new GroupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                null,
                new GroupService(groupMemberRepository, groupChatRepository, groupRepository, null, null),
                null,
                null,
                null,
                null);

        List<GroupItemDTO> groupList = appService.getGroupList(new GroupListQuery(1L));

        assertThat(groupList)
                .extracting(GroupItemDTO::getGroupName)
                .containsExactly("alpha", "beta");

        assertThat(groupList)
                .extracting(GroupItemDTO::getChatId)
                .containsExactly(101L, 102L);
        assertThat(groupList)
                .extracting(GroupItemDTO::getUnreadMessageCount)
                .containsExactly(2, 0);
        assertThat(groupList)
                .extracting(GroupItemDTO::getMemberCount)
                .containsExactly(2, 1);
        assertThat(groupRepository.findByUserIdCount).isEqualTo(1);
    }

    @Test
    void getGroupListSkipsDismissedGroups() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(3)));
        groupRepository.groups.add(dismissedGroup(1002L, 1L, "beta"));

        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1002L, 1L));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1002L, 1L));

        GroupAppService appService = groupAppService(groupRepository, groupChatRepository, groupMemberRepository);

        List<GroupItemDTO> groupList = appService.getGroupList(new GroupListQuery(1L));

        assertThat(groupList)
                .extracting(GroupItemDTO::getGroupName)
                .containsExactly("alpha");
    }

    @Test
    void getGroupDetailReturnsGroupAndMembersForMember() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(3)));

        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 2L, "member-in-group"));
        groupMemberRepository.members.add(groupMember(1001L, 1L, "owner-in-group"));
        groupMemberRepository.members.add(groupMember(1001L, 3L, "another-member-in-group"));

        MemoryFriendRepository friendRepository = new MemoryFriendRepository();
        friendRepository.friends.add(friend(1L, 2L, "member", "member-friend"));

        MemoryUserRepository userRepository = new MemoryUserRepository();
        userRepository.users.add(user(1L, "owner"));
        userRepository.users.add(user(2L, "member"));
        userRepository.users.add(user(3L, "another-member"));

        GroupAppService appService = new GroupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                null,
                new GroupService(groupMemberRepository, groupChatRepository, groupRepository, friendRepository, userRepository),
                null,
                null,
                null,
                null);

        GroupDetailDTO detail = appService.getGroupDetail(new GroupDetailQuery(1L, 1001L));

        assertThat(detail.getGroupId()).isEqualTo(1001L);
        assertThat(detail.getChatId()).isEqualTo(101L);
        assertThat(detail.getGroupName()).isEqualTo("alpha");
        assertThat(detail.getMemberCount()).isEqualTo(3);

        assertThat(detail.getMembers())
                .extracting(GroupDetailDTO.Member::getUserId)
                .containsExactly(1L, 2L, 3L);
        assertThat(detail.getMembers())
                .extracting(GroupDetailDTO.Member::getDisplayName)
                .containsExactly("owner-in-group", "member-friend", "another-member-in-group");
    }

    @Test
    void getGroupDetailRejectsDismissedGroup() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(dismissedGroup(1001L, 1L, "alpha"));

        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));

        GroupAppService appService = groupAppService(groupRepository, groupChatRepository, groupMemberRepository);

        assertThatThrownBy(() -> appService.getGroupDetail(new GroupDetailQuery(1L, 1001L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getGroupDetailRejectsUserOutsideGroup() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha"));

        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));

        GroupAppService appService = new GroupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                null,
                new GroupService(groupMemberRepository, groupChatRepository, groupRepository, null, null),
                null,
                null,
                null,
                null);

        assertThatThrownBy(() -> appService.getGroupDetail(new GroupDetailQuery(2L, 1001L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getGroupDetailReturnsNotFoundBeforeCheckingMembership() {
        GroupAppService appService = groupAppService(
                new MemoryGroupRepository(),
                new MemoryGroupChatRepository(),
                new MemoryGroupMemberRepository());

        assertThatThrownBy(() -> appService.getGroupDetail(new GroupDetailQuery(2L, 1001L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("群组不存在");
    }

    @Test
    void transferGroupOwnerChangesOwnerWhenOperatorIsCurrentOwner() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(2)));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        GroupAppService appService = groupAppService(groupRepository, new MemoryGroupChatRepository(), groupMemberRepository);

        appService.transferGroupOwner(new GroupTransferOwnerCmd(1L, 1001L, 2L));

        assertThat(groupRepository.savedGroup.getOwnerId().value()).isEqualTo(2L);
    }

    @Test
    void memberLeaveCount() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(2)));
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        GroupAppService appService = groupAppService(
                groupRepository, groupChatRepository, groupMemberRepository, eventPublisher);

        appService.leaveGroup(new GroupLeaveCmd(2L, 1001L));

        assertThat(groupRepository.savedGroup.getMemberCount().value()).isEqualTo(1);
        assertThat(groupMemberRepository.removedPairs).containsExactly("1001:2");
        assertThat(groupChatRepository.removedPairs).isEmpty();
        assertThat(eventPublisher.events)
                .singleElement()
                .isInstanceOfSatisfying(GroupMemberRemovedEvent.class, event -> {
                    assertThat(event.getGroupId().value()).isEqualTo(1001L);
                    assertThat(event.getUserId().value()).isEqualTo(2L);
                });

        appService.onGroupMemberRemoved((GroupMemberRemovedEvent) eventPublisher.events.getFirst());

        assertThat(groupChatRepository.removedPairs).containsExactly("1001:2");
    }

    @Test
    void ownerCannotMemberLeaveGroupBeforeTransfer() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(2)));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        GroupAppService appService = groupAppService(groupRepository, new MemoryGroupChatRepository(), groupMemberRepository);

        assertThatThrownBy(() -> appService.leaveGroup(new GroupLeaveCmd(1L, 1001L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("群主不能直接退群");
    }

    @Test
    void kickGroupMemberLogicallyDeletesMemberAndChatAndDecreasesMemberCount() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(2)));
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        GroupAppService appService = groupAppService(
                groupRepository, groupChatRepository, groupMemberRepository, eventPublisher);

        appService.kickGroupMember(new GroupKickMemberCmd(1L, 1001L, 2L));

        assertThat(groupRepository.savedGroup.getMemberCount().value()).isEqualTo(1);
        assertThat(groupMemberRepository.removedPairs).containsExactly("1001:2");
        assertThat(groupChatRepository.removedPairs).isEmpty();
        assertThat(eventPublisher.events)
                .singleElement()
                .isInstanceOfSatisfying(GroupMemberRemovedEvent.class, event -> {
                    assertThat(event.getGroupId().value()).isEqualTo(1001L);
                    assertThat(event.getUserId().value()).isEqualTo(2L);
                });

        appService.onGroupMemberRemoved((GroupMemberRemovedEvent) eventPublisher.events.getFirst());

        assertThat(groupChatRepository.removedPairs).containsExactly("1001:2");
    }

    @Test
    void nonOwnerCannotKickGroupMember() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(2)));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        GroupAppService appService = groupAppService(groupRepository, new MemoryGroupChatRepository(), groupMemberRepository);

        assertThatThrownBy(() -> appService.kickGroupMember(new GroupKickMemberCmd(2L, 1001L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有群主");
    }

    @Test
    void nonOwnerCannotProbeMissingGroupMemberWhenKicking() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(2)));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        GroupAppService appService = groupAppService(groupRepository, new MemoryGroupChatRepository(), groupMemberRepository);

        assertThatThrownBy(() -> appService.kickGroupMember(new GroupKickMemberCmd(2L, 1001L, 99L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有群主");
    }

    @Test
    void groupOwnerCanChangeNotification() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(2)));
        GroupAppService appService = groupAppService(groupRepository, new MemoryGroupChatRepository(), new MemoryGroupMemberRepository());

        appService.changeGroupNotification(new GroupNotificationChangeCmd(1L, 1001L, "new notice"));

        assertThat(groupRepository.savedGroup.getNotification().value()).isEqualTo("new notice");
    }

    @Test
    void nonOwnerCannotChangeNotification() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(2)));
        GroupAppService appService = groupAppService(groupRepository, new MemoryGroupChatRepository(), new MemoryGroupMemberRepository());

        assertThatThrownBy(() -> appService.changeGroupNotification(new GroupNotificationChangeCmd(2L, 1001L, "new notice")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有群主");
    }

    @Test
    void memberCanChangeOwnGroupMemberAlias() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha", new MemberCount(2)));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        GroupAppService appService = groupAppService(groupRepository, new MemoryGroupChatRepository(), groupMemberRepository);

        appService.changeGroupMemberAlias(new GroupMemberAliasChangeCmd(2L, 1001L, "member-name"));

        assertThat(groupMemberRepository.savedMembers).hasSize(1);
        assertThat(groupMemberRepository.savedMembers.getFirst().getUserAlias().value()).isEqualTo("member-name");
    }

    private ImGroupChat groupChat(Long chatId, Long groupId, Long userId) {
        return groupChat(chatId, groupId, userId, null, 0);
    }

    private ImGroupChat groupChat(Long chatId, Long groupId, Long userId, Long lastMessageId, int unreadMessageCount) {
        return ImGroupChat.builder()
                .id(new ImChatId(chatId))
                .groupId(new GroupId(groupId))
                .userId(new UserId(userId))
                .type(ImChatType.GROUP)
                .lastMessageId(lastMessageId == null ? null : new ImMessageId(lastMessageId))
                .unreadMessageCount(unreadMessageCount)
                .build();
    }

    private GroupMember groupMember(Long groupId, Long userId) {
        return groupMember(groupId, userId, null);
    }

    private GroupMember groupMember(Long groupId, Long userId, String userAlias) {
        GroupId imGroupId = new GroupId(groupId);
        UserId imUserId = new UserId(userId);
        return GroupMember.builder()
                .id(new MemberId(imGroupId, imUserId))
                .groupId(imGroupId)
                .userId(imUserId)
                .userAlias(userAlias == null ? null : new GroupUserAlias(userAlias))
                .joinTime(java.time.LocalDateTime.now())
                .build();
    }

    private Friend friend(Long userId, Long friendUserId, String friendName, String friendAlias) {
        Friend friend = new Friend();
        friend.setUserId(new UserId(userId));
        friend.setFriendUserId(new UserId(friendUserId));
        friend.setFriendName(new UserName(friendName));
        friend.setFriendAlias(friendAlias == null ? null : new FriendAlias(friendAlias));
        return friend;
    }

    private User user(Long userId, String username) {
        return new User(new UserId(userId), null, new UserName(username), null);
    }

    private Group group(Long groupId, Long ownerId, String name) {
        return group(groupId, ownerId, name, new MemberCount(1), GroupStatus.ACTIVE);
    }

    private Group group(Long groupId, Long ownerId, String name, MemberCount memberCount) {
        return group(groupId, ownerId, name, memberCount, GroupStatus.ACTIVE);
    }

    private Group group(Long groupId, Long ownerId, String name, MemberCount memberCount, GroupStatus status) {
        return Group.builder()
                .id(new GroupId(groupId))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(ownerId))
                .name(new GroupName(name))
                .notification(new GroupNotification(""))
                .memberCount(memberCount)
                .status(status)
                .build();
    }

    private Group dismissedGroup(Long groupId, Long ownerId, String name) {
        return group(groupId, ownerId, name, new MemberCount(1), GroupStatus.DISMISSED);
    }

    private GroupAppService groupAppService(MemoryGroupRepository groupRepository,
                                            MemoryGroupChatRepository groupChatRepository,
                                            MemoryGroupMemberRepository groupMemberRepository) {
        return groupAppService(groupRepository, groupChatRepository, groupMemberRepository, new NoopDomainEventPublisher());
    }

    private GroupAppService groupAppService(MemoryGroupRepository groupRepository,
                                            MemoryGroupChatRepository groupChatRepository,
                                            MemoryGroupMemberRepository groupMemberRepository,
                                            DomainEventPublisher eventPublisher) {
        return groupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                null,
                new FixedSnowflakeId(3000L),
                eventPublisher);
    }

    private GroupAppService groupAppService(MemoryGroupRepository groupRepository,
                                            MemoryGroupChatRepository groupChatRepository,
                                            MemoryGroupMemberRepository groupMemberRepository,
                                            MemoryGroupInboxMessageRepository groupInboxMessageRepository,
                                            FixedSnowflakeId snowflakeId) {
        return new GroupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                groupInboxMessageRepository,
                new GroupService(
                        groupMemberRepository, groupChatRepository, groupRepository, null, null),
                new ImChatService(null, null, null, groupChatRepository, null, snowflakeId),
                new ImMessageService(groupInboxMessageRepository, null, snowflakeId),
                snowflakeId,
                new NoopDomainEventPublisher());
    }

    private GroupAppService groupAppService(MemoryGroupRepository groupRepository,
                                            MemoryGroupChatRepository groupChatRepository,
                                            MemoryGroupMemberRepository groupMemberRepository,
                                            MemoryGroupInboxMessageRepository groupInboxMessageRepository,
                                            FixedSnowflakeId snowflakeId,
                                            DomainEventPublisher eventPublisher) {
        return new GroupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                groupInboxMessageRepository,
                new GroupService(
                        groupMemberRepository, groupChatRepository, groupRepository, null, null),
                new ImChatService(null, null, null, groupChatRepository, null, snowflakeId),
                new ImMessageService(groupInboxMessageRepository, null, snowflakeId),
                snowflakeId,
                eventPublisher);
    }

    private static class FixedSnowflakeId extends SnowflakeId {
        private long next;

        FixedSnowflakeId(long next) {
            super(new TestMachineId());
            this.next = next;
        }

        @Override
        public synchronized Long next() {
            return next++;
        }
    }

    private static class TestMachineId implements ISnowflakeMachineId {
        @Override
        public long getDataCenterId() {
            return 1L;
        }

        @Override
        public long getMachineId() {
            return 1L;
        }
    }

    private static class SignedInSessionRepository implements SessionRepository {
        private Session session;

        @Override
        public Optional<Session> find(UserId userId) {
            session = new Session(userId);
            session.onSignIn();
            return Optional.of(session);
        }

        @Override
        public void save(Session session) {
            this.session = session;
        }
    }

    private static class MemoryGroupRepository implements GroupRepository {
        private final List<Group> groups = new ArrayList<>();
        private Group savedGroup;
        private int findByUserIdCount;

        @Override
        public Optional<Group> find(GroupId groupId) {
            if (savedGroup != null && savedGroup.getId().equals(groupId)) {
                return Optional.of(savedGroup);
            }
            return groups.stream()
                    .filter(group -> group.getId().equals(groupId))
                    .findFirst();
        }

        @Override
        public List<Group> find(List<GroupId> groupIds) {
            List<Group> result = groups.stream()
                    .filter(group -> groupIds.contains(group.getId()))
                    .collect(Collectors.toList());
            if (savedGroup != null && groupIds.contains(savedGroup.getId())) {
                result.add(savedGroup);
            }
            return result;
        }

        @Override
        public List<Group> find(UserId userId) {
            findByUserIdCount++;
            return groups;
        }

        @Override
        public void save(Group group) {
            savedGroup = group;
        }
    }

    private static class MemoryGroupChatRepository implements ImGroupChatRepository {
        private final List<ImGroupChat> groupChats = new ArrayList<>();
        private List<ImGroupChat> savedGroupChats = new ArrayList<>();
        private final List<String> removedPairs = new ArrayList<>();
        private int saveAllCount;

        @Override
        public Optional<ImGroupChat> find(ImChatId chatId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getId().equals(chatId))
                    .findFirst();
        }

        @Override
        public Optional<ImGroupChat> find(GroupId groupId, UserId userId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getGroupId().equals(groupId))
                    .filter(groupChat -> groupChat.getUserId().equals(userId))
                    .findFirst();
        }

        @Override
        public List<ImGroupChat> find(GroupId groupId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getGroupId().equals(groupId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(java.util.Collection<GroupId> groupIds) {
            return groupChats.stream()
                    .filter(groupChat -> groupIds.contains(groupChat.getGroupId()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(UserId userId, java.util.Collection<GroupId> groupIds) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getUserId().equals(userId))
                    .filter(groupChat -> groupIds.contains(groupChat.getGroupId()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(UserId userId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getUserId().equals(userId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(GroupId groupId, List<UserId> memberIds) {
            return Collections.emptyList();
        }

        @Override
        public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer) {
            return Collections.emptyList();
        }

        @Override
        public void save(ImGroupChat groupChat) {
            savedGroupChats = Collections.singletonList(groupChat);
        }

        @Override
        public void save(List<ImGroupChat> groupChats) {
            saveAllCount++;
            savedGroupChats = new ArrayList<>(groupChats);
            for (ImGroupChat groupChat : groupChats) {
                this.groupChats.removeIf(savedChat -> savedChat.getGroupId().equals(groupChat.getGroupId())
                        && savedChat.getUserId().equals(groupChat.getUserId()));
                this.groupChats.add(groupChat);
            }
        }

        @Override
        public boolean contain(ImChatId chatId, UserId userId) {
            return find(chatId).map(chat -> chat.belongsTo(userId)).orElse(false);
        }

        @Override
        public void remove(GroupId groupId, UserId userId) {
            removedPairs.add(groupId.value() + ":" + userId.value());
        }
    }

    private static class MemoryGroupMemberRepository implements GroupMemberRepository {
        private final List<GroupMember> members = new ArrayList<>();
        private List<GroupMember> savedMembers = new ArrayList<>();
        private final List<String> removedPairs = new ArrayList<>();
        private int findByGroupIdCount;

        @Override
        public List<GroupMember> find(GroupId groupId) {
            findByGroupIdCount++;
            return members.stream()
                    .filter(member -> member.getGroupId().equals(groupId))
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<GroupMember> find(GroupId groupId, UserId userId) {
            return members.stream()
                    .filter(member -> member.getGroupId().equals(groupId))
                    .filter(member -> member.getUserId().equals(userId))
                    .findFirst();
        }

        @Override
        public boolean contain(GroupId groupId, UserId userId) {
            return find(groupId, userId).isPresent();
        }

        @Override
        public void save(List<GroupMember> members) {
            savedMembers = new ArrayList<>(members);
            this.members.addAll(members);
        }

        @Override
        public void save(GroupMember member) {
            savedMembers = Collections.singletonList(member);
        }

        @Override
        public void remove(GroupMember groupMember) {
            removedPairs.add(groupMember.getGroupId().value() + ":" + groupMember.getUserId().value());
        }

    }

    private static class MemoryGroupInboxMessageRepository implements ImGroupInboxMessageRepository {
        private List<ImGroupInboxMessage> savedMessages = new ArrayList<>();

        @Override
        public void save(ImGroupInboxMessage message) {
            savedMessages = Collections.singletonList(message);
        }

        @Override
        public void save(List<ImGroupInboxMessage> messages) {
            savedMessages = new ArrayList<>(messages);
        }

        @Override
        public boolean contain(ImChatId chatId, UserId userId, ImMessageToken token) {
            return false;
        }

        @Override
        public Optional<ImGroupInboxMessage> find(ImChatId chatId, UserId userId, ImMessageId messageId) {
            return Optional.empty();
        }

        @Override
        public List<ImGroupInboxMessage> findByGroupIdAndMessageId(GroupId groupId, ImMessageId messageId) {
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupInboxMessage> findUnreadMessages(ImChatId chatId, UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupInboxMessage> queryHistory(
                ImChatId chatId, UserId userId, ImMessageId lastMessageId, Integer count) {
            return Collections.emptyList();
        }

        @Override
        public ImGroupInboxMessage queryDetail(ImChatId chatId, UserId userId, ImMessageToken token) {
            return null;
        }

        @Override
        public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId userId) {
            return Collections.emptyList();
        }
    }

    private static class NoopDomainEventPublisher implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {
        }

        @Override
        public void publish(List<DomainEvent> eventList) {
        }
    }

    private static class MemoryDomainEventPublisher implements DomainEventPublisher {
        private final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            events.add(event);
        }

        @Override
        public void publish(List<DomainEvent> eventList) {
            events.addAll(eventList);
        }
    }

    private static class MemoryFriendRepository implements FriendRepository {
        private final List<Friend> friends = new ArrayList<>();

        @Override
        public List<Friend> find(UserId userId) {
            return friends.stream()
                    .filter(friend -> friend.getUserId().equals(userId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Friend> find(UserId userId, List<UserId> friendUserIds) {
            return find(userId).stream()
                    .filter(friend -> friendUserIds.contains(friend.getFriendUserId()))
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<Friend> find(FriendEdge edge) {
            return find(edge.userId()).stream()
                    .filter(friend -> friend.getFriendUserId().equals(edge.friendUserId()))
                    .findFirst();
        }

        @Override
        public boolean contain(UserId userId, UserId friendUserId) {
            return find(new FriendEdge(userId, friendUserId)).isPresent();
        }

        @Override
        public boolean isFriendshipActive(UserId userId, UserId friendUserId) {
            return find(new FriendEdge(userId, friendUserId))
                    .map(Friend::isNormal)
                    .orElse(false);
        }

        @Override
        public void save(Friend friend) {
        }

        @Override
        public void remove(Friend friend) {
            friends.removeIf(savedFriend -> savedFriend.getUserId().equals(friend.getUserId())
                    && savedFriend.getFriendUserId().equals(friend.getFriendUserId()));
        }
    }

    private static class MemoryUserRepository implements UserRepository {
        private final List<User> users = new ArrayList<>();

        @Override
        public Optional<User> find(UserId userId) {
            return users.stream()
                    .filter(user -> user.getId().equals(userId))
                    .findFirst();
        }

        @Override
        public List<User> find(List<UserId> userIds) {
            return users.stream()
                    .filter(user -> userIds.contains(user.getId()))
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<User> find(UserEmail email) {
            return Optional.empty();
        }

        @Override
        public void save(User user) {
        }

        @Override
        public void remove(User user) {
        }

        @Override
        public boolean contain(UserEmail email) {
            return false;
        }
    }
}
