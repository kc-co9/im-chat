package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendAlias;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.group.Group;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.group.GroupMember;
import com.co.kc.imchat.domain.group.GroupMemberRepository;
import com.co.kc.imchat.domain.group.GroupName;
import com.co.kc.imchat.domain.group.GroupRepository;
import com.co.kc.imchat.domain.group.GroupService;
import com.co.kc.imchat.domain.group.GroupStatus;
import com.co.kc.imchat.domain.group.GroupUserAlias;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.group.MemberId;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserName;
import com.co.kc.imchat.domain.user.UserRepository;
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
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupAppServiceTest {

    @Test
    void createGroupPersistsMembersAndCreatesChatForEachMember() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        MemoryGroupInboxMessageRepository groupInboxMessageRepository = new MemoryGroupInboxMessageRepository();
        SignedInSessionRepository sessionRepository = new SignedInSessionRepository();
        FixedSnowflakeId snowflakeId = new FixedSnowflakeId(1000L);
        GroupAppService appService = new GroupAppService(
                snowflakeId,
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                new GroupService(groupMemberRepository, groupChatRepository, null, null),
                new ImChatService(snowflakeId, null, null, null, null, sessionRepository),
                groupInboxMessageRepository,
                new ImMessageService(snowflakeId),
                new NoopDomainEventPublisher());
        GroupCreateCmd command = new GroupCreateCmd(1L, Collections.singletonList(2L), "group");

        GroupCreateDTO result = appService.createGroup(command);

        assertThat(result.getGroupId()).isEqualTo(1000L);
        assertThat(result.getChatId()).isEqualTo(1001L);
        assertThat(groupRepository.savedGroup.getId().getValue()).isEqualTo(1000L);

        assertThat(groupMemberRepository.savedMembers)
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(1L, 2L);

        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getUserId().getValue())
                .containsExactly(1L, 2L);
        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getId().getValue())
                .containsExactly(1001L, 1002L);
        assertThat(groupChatRepository.saveAllCount).isEqualTo(1);
        assertThat(sessionRepository.session.getChatId().getValue()).isEqualTo(1001L);
    }

    @Test
    void createGroupStoresSystemMessageForEachMemberAndUpdatesChats() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        MemoryGroupInboxMessageRepository groupInboxMessageRepository = new MemoryGroupInboxMessageRepository();
        FixedSnowflakeId snowflakeId = new FixedSnowflakeId(1000L);
        GroupAppService appService = new GroupAppService(
                snowflakeId,
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                new GroupService(groupMemberRepository, groupChatRepository, null, null),
                new ImChatService(snowflakeId, null, null, null, null, new SignedInSessionRepository()),
                groupInboxMessageRepository,
                new ImMessageService(snowflakeId),
                new NoopDomainEventPublisher());
        GroupCreateCmd command = new GroupCreateCmd(1L, Collections.singletonList(2L), "group");

        appService.createGroup(command);

        assertThat(groupInboxMessageRepository.savedMessages).hasSize(2);
        assertThat(groupInboxMessageRepository.savedMessages)
                .extracting(message -> message.getUserId().getValue())
                .containsExactly(1L, 2L);

        assertThat(groupInboxMessageRepository.savedMessages)
                .allSatisfy(message -> {
                    assertThat(message.getId().getValue()).isEqualTo(1003L);
                    assertThat(message.getSenderId().getValue()).isEqualTo(1L);
                    assertThat(message.getContent().getType()).isEqualTo(ImMessageType.SYSTEM);
                    assertThat(message.getContent().getValue()).isEqualTo("群聊已创建");
                });
        assertThat(groupInboxMessageRepository.savedMessages)
                .extracting(ImGroupInboxMessage::getStatus)
                .containsExactly(ImGroupMessageStatus.READ, ImGroupMessageStatus.SENT);

        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getLastMessageId().getValue())
                .containsExactly(1003L, 1003L);
        assertThat(groupChatRepository.savedGroupChats)
                .extracting(ImGroupChat::getUnreadMessageCount)
                .containsExactly(0, 1);
    }

    @Test
    void inviteGroupMembersCreatesMemberAndChatRowsForNewMembers() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "group"));

        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));

        GroupAppService appService = new GroupAppService(
                new FixedSnowflakeId(3000L),
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                new GroupService(groupMemberRepository, groupChatRepository, null, null),
                new ImChatService(new FixedSnowflakeId(3000L), null, null, null, null, null),
                null,
                null,
                null);
        GroupInviteMembersCmd command = new GroupInviteMembersCmd(1L, 1001L, Collections.singletonList(2L));

        appService.inviteGroupMembers(command);

        assertThat(groupMemberRepository.savedMembers)
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(2L);

        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getUserId().getValue())
                .containsExactly(2L);
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
    void dismissGroupMarksGroupDismissedAndStoresSystemMessage() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "group"));

        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));

        MemoryGroupInboxMessageRepository groupInboxMessageRepository = new MemoryGroupInboxMessageRepository();
        GroupAppService appService = groupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                groupInboxMessageRepository,
                new FixedSnowflakeId(4000L));

        appService.dismissGroup(new GroupDismissCmd(1L, 1001L));

        assertThat(groupRepository.savedGroup.getStatus()).isEqualTo(GroupStatus.DISMISSED);
        assertThat(groupInboxMessageRepository.savedMessages).hasSize(2);

        assertThat(groupInboxMessageRepository.savedMessages)
                .extracting(message -> message.getUserId().getValue())
                .containsExactly(1L, 2L);

        assertThat(groupInboxMessageRepository.savedMessages)
                .allSatisfy(message -> {
                    assertThat(message.getId().getValue()).isEqualTo(4000L);
                    assertThat(message.getSenderId().getValue()).isEqualTo(1L);
                    assertThat(message.getContent().getType()).isEqualTo(ImMessageType.SYSTEM);
                    assertThat(message.getContent().getValue()).isEqualTo("群聊已解散");
                });
        assertThat(groupInboxMessageRepository.savedMessages)
                .extracting(ImGroupInboxMessage::getStatus)
                .containsExactly(ImGroupMessageStatus.READ, ImGroupMessageStatus.SENT);

        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getLastMessageId().getValue())
                .containsExactly(4000L, 4000L);
        assertThat(groupChatRepository.savedGroupChats)
                .extracting(ImGroupChat::getUnreadMessageCount)
                .containsExactly(0, 1);
    }

    @Test
    void getGroupListReturnsGroupsJoinedByUser() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha"));
        groupRepository.groups.add(group(1002L, 2L, "beta"));

        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L, 901L, 2));
        groupChatRepository.groupChats.add(groupChat(102L, 1002L, 1L, null, 0));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        groupMemberRepository.members.add(groupMember(1002L, 1L));

        GroupAppService appService = new GroupAppService(
                null,
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                new GroupService(groupMemberRepository, groupChatRepository, null, null),
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
        assertThat(groupRepository.findByUserIdCount).isEqualTo(1);
    }

    @Test
    void getGroupListSkipsDismissedGroups() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha"));
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
        groupRepository.groups.add(group(1001L, 1L, "alpha"));

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
                null,
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                new GroupService(groupMemberRepository, groupChatRepository, friendRepository, userRepository),
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
                null,
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                null,
                null,
                null,
                null,
                null);

        assertThatThrownBy(() -> appService.getGroupDetail(new GroupDetailQuery(2L, 1001L)))
                .isInstanceOf(BusinessException.class);
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
        return Group.builder()
                .id(new GroupId(groupId))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(ownerId))
                .name(new GroupName(name))
                .build();
    }

    private Group dismissedGroup(Long groupId, Long ownerId, String name) {
        Group group = group(groupId, ownerId, name);
        group.setStatus(GroupStatus.DISMISSED);
        return group;
    }

    private GroupAppService groupAppService(MemoryGroupRepository groupRepository,
                                            MemoryGroupChatRepository groupChatRepository,
                                            MemoryGroupMemberRepository groupMemberRepository) {
        return groupAppService(
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                null,
                new FixedSnowflakeId(3000L));
    }

    private GroupAppService groupAppService(MemoryGroupRepository groupRepository,
                                            MemoryGroupChatRepository groupChatRepository,
                                            MemoryGroupMemberRepository groupMemberRepository,
                                            MemoryGroupInboxMessageRepository groupInboxMessageRepository,
                                            FixedSnowflakeId snowflakeId) {
        return new GroupAppService(
                snowflakeId,
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                new GroupService(groupMemberRepository, groupChatRepository, null, null),
                new ImChatService(snowflakeId, null, null, null, null, null),
                groupInboxMessageRepository,
                new ImMessageService(snowflakeId),
                new NoopDomainEventPublisher());
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

    private static class TestMachineId implements com.co.kc.imchat.support.identity.snowflake.ISnowflakeMachineId {
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
        public Session find(UserId userId) {
            session = new Session(userId);
            session.onSignIn();
            return session;
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
        public Group find(GroupId groupId) {
            if (savedGroup != null && savedGroup.getId().equals(groupId)) {
                return savedGroup;
            }
            return groups.stream()
                    .filter(group -> group.getId().equals(groupId))
                    .findFirst()
                    .orElse(null);
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
        private int saveAllCount;

        @Override
        public ImGroupChat find(ImChatId chatId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getId().equals(chatId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public ImGroupChat find(GroupId groupId, UserId userId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getGroupId().equals(groupId))
                    .filter(groupChat -> groupChat.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);
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
        public List<ImGroupChat> findByUserIdAndChatIds(UserId userId, List<ImChatId> chatIds) {
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupChat> findByUserIdsAndGroupId(GroupId groupId, List<UserId> userIds) {
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
        public void saveAll(List<ImGroupChat> groupChats) {
            saveAllCount++;
            savedGroupChats = new ArrayList<>(groupChats);
        }

        @Override
        public boolean contain(ImChatId chatId, UserId userId) {
            return find(chatId) != null && find(chatId).contain(userId);
        }
    }

    private static class MemoryGroupMemberRepository implements GroupMemberRepository {
        private final List<GroupMember> members = new ArrayList<>();
        private List<GroupMember> savedMembers = new ArrayList<>();
        private int findByGroupIdCount;
        private int countByGroupIdsCount;

        @Override
        public List<GroupMember> find(GroupId groupId) {
            findByGroupIdCount++;
            return members.stream()
                    .filter(member -> member.getGroupId().equals(groupId))
                    .collect(Collectors.toList());
        }

        @Override
        public GroupMember find(GroupId groupId, UserId userId) {
            return members.stream()
                    .filter(member -> member.getGroupId().equals(groupId))
                    .filter(member -> member.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public void saveAll(List<GroupMember> members) {
            savedMembers = new ArrayList<>(members);
            this.members.addAll(members);
        }

        @Override
        public Map<GroupId, Integer> countByGroupIds(List<GroupId> groupIds) {
            countByGroupIdsCount++;
            return members.stream()
                    .filter(member -> groupIds.contains(member.getGroupId()))
                    .collect(Collectors.groupingBy(GroupMember::getGroupId, Collectors.summingInt(member -> 1)));
        }
    }

    private static class MemoryGroupInboxMessageRepository implements ImGroupInboxMessageRepository {
        private List<ImGroupInboxMessage> savedMessages = new ArrayList<>();

        @Override
        public void save(ImGroupInboxMessage message) {
            savedMessages = Collections.singletonList(message);
        }

        @Override
        public void saveAll(List<ImGroupInboxMessage> messages) {
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
        public void publish(com.co.kc.imchat.domain.shared.DomainEvent event) {
        }

        @Override
        public void publish(List<com.co.kc.imchat.domain.shared.DomainEvent> eventList) {
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
        public Friend find(UserId userId, UserId friendUserId) {
            return find(userId).stream()
                    .filter(friend -> friend.getFriendUserId().equals(friendUserId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public void save(Friend friend) {
        }

        @Override
        public void remove(Friend friend) {
        }
    }

    private static class MemoryUserRepository implements UserRepository {
        private final List<User> users = new ArrayList<>();

        @Override
        public User find(UserId userId) {
            return users.stream()
                    .filter(user -> user.getId().equals(userId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<User> find(List<UserId> userIds) {
            return users.stream()
                    .filter(user -> userIds.contains(user.getId()))
                    .collect(Collectors.toList());
        }

        @Override
        public User find(com.co.kc.imchat.domain.user.UserEmail email) {
            return null;
        }

        @Override
        public void save(User user) {
        }

        @Override
        public void remove(User user) {
        }

        @Override
        public boolean contain(com.co.kc.imchat.domain.user.UserEmail email) {
            return false;
        }
    }
}
