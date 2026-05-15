package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.chat.ImChatName;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatStatus;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImUserChatDescriptor;
import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendAlias;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.group.Group;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.group.GroupMember;
import com.co.kc.imchat.domain.group.GroupName;
import com.co.kc.imchat.domain.group.GroupRepository;
import com.co.kc.imchat.domain.group.GroupRoster;
import com.co.kc.imchat.domain.group.GroupService;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.group.GroupStatus;
import com.co.kc.imchat.domain.group.GroupUserAlias;
import com.co.kc.imchat.domain.group.MemberDescriptor;
import com.co.kc.imchat.domain.group.MemberDisplayName;
import com.co.kc.imchat.domain.group.MemberId;
import com.co.kc.imchat.domain.group.UserGroupDescriptor;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserName;
import com.co.kc.imchat.domain.user.UserRepository;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.transformer.domain.ImChatDomainTransformer;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupDomainServiceTest {

    @Test
    void createRosterCreatesMembersWithOwnerIncluded() {
        GroupService service = new GroupService(null, null, null, null);
        GroupId groupId = new GroupId(1001L);

        GroupRoster roster = service.createRoster(
                groupId,
                new UserId(1L),
                Arrays.asList(new UserId(2L), new UserId(1L)));

        assertThat(roster.getMembers())
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(1L, 2L);
        assertThat(roster.getMembers())
                .allSatisfy(member -> assertThat(member.getJoinTime()).isNotNull());
    }

    @Test
    void createRosterAllowsEmptyMemberIdsAndOnlyKeepsOwner() {
        GroupService service = new GroupService(null, null, null, null);
        GroupId groupId = new GroupId(1001L);

        GroupRoster roster = service.createRoster(groupId, new UserId(1L), null);

        assertThat(roster.getMembers())
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(1L);
    }

    @Test
    void groupRosterInvitesOnlyNewMembers() {
        GroupId groupId = new GroupId(1001L);
        GroupRoster roster = new GroupRoster(
                groupId,
                Collections.singletonList(groupMember(groupId, 1L)));

        List<GroupMember> members = roster.invite(
                new UserId(1L),
                Arrays.asList(new UserId(1L), new UserId(2L), new UserId(2L)));

        assertThat(members)
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(2L);
    }

    @Test
    void groupRosterRejectsInviterOutsideGroup() {
        GroupId groupId = new GroupId(1001L);
        GroupRoster roster = new GroupRoster(
                groupId,
                Collections.singletonList(groupMember(groupId, 1L)));

        assertThatThrownBy(() -> roster.invite(new UserId(2L), Collections.singletonList(new UserId(3L))))
                .isInstanceOf(RuntimeException.class);
    }


    @Test
    void emptyGroupAliasUsesGroupNameAsChatName() {
        DbImGroupChat row = new DbImGroupChat();
        row.setChatId(101L);
        row.setGroupId(1001L);
        row.setUserId(1L);
        row.setGroupAlias("");
        row.setLastMessageId(0L);
        row.setReadMessageId(0L);
        row.setUnreadMessageCount(0);
        ImGroupChat chat = ImChatDomainTransformer.INSTANCE.imGroupChatFrom(row);
        Group group = Group.builder()
                .id(new GroupId(1001L))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(1L))
                .name(new GroupName("group"))
                .build();
        ImChatService service = new ImChatService(null, null, null, null, null, null);

        ImChatName chatName = service.obtainGroupChatName(group, chat.getGroupAlias());

        assertThat(chatName.getValue()).isEqualTo("group");
    }

    @Test
    void emptyGroupMemberUserAliasMapsToUnsetAlias() {
        DbImGroupMember row = new DbImGroupMember();
        row.setGroupId(1001L);
        row.setUserId(1L);
        row.setUserAlias("");
        row.setJoinTime(java.time.LocalDateTime.now());

        GroupMember member = ImChatDomainTransformer.INSTANCE.imGroupMemberFrom(row);

        assertThat(member.getUserAlias()).isNull();
    }

    @Test
    void describeUserGroupsCombinesGroupsWithUserGroupChats() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        GroupService service = new GroupService(null, groupChatRepository, null, null);
        Group alpha = group(1001L, 1L, "alpha");
        Group beta = group(1002L, 1L, "beta");
        Group missingChat = group(1003L, 1L, "missing");
        ImGroupChat alphaChat = groupChat(101L, 1001L, 1L);
        ImGroupChat betaChat = groupChat(102L, 1002L, 1L);
        ImGroupChat otherUserAlphaChat = groupChat(201L, 1001L, 2L);
        groupChatRepository.groupChats.add(alphaChat);
        groupChatRepository.groupChats.add(betaChat);
        groupChatRepository.groupChats.add(otherUserAlphaChat);

        List<UserGroupDescriptor> descriptors = service.describeUserGroups(
                new UserId(1L),
                Arrays.asList(alpha, beta, missingChat));

        assertThat(descriptors)
                .extracting(descriptor -> descriptor.getId().getValue())
                .containsExactly(1001L, 1002L);
        assertThat(descriptors)
                .extracting(descriptor -> descriptor.getChat().getId().getValue())
                .containsExactly(101L, 102L);
        assertThat(descriptors)
                .extracting(descriptor -> descriptor.getName().getValue())
                .containsExactly("alpha", "beta");
    }

    @Test
    void describeGroupMembersUsesFriendAliasThenGroupAliasThenUsername() {
        MemoryFriendRepository friendRepository = new MemoryFriendRepository();
        friendRepository.friends.add(friend(1L, 2L, "bob", "friend-bob"));
        MemoryUserRepository userRepository = new MemoryUserRepository();
        userRepository.users.add(user(2L, "bob"));
        userRepository.users.add(user(3L, "carol"));
        userRepository.users.add(user(4L, "dave"));
        GroupId groupId = new GroupId(1001L);
        GroupService service = new GroupService(null, null, friendRepository, userRepository);

        List<MemberDescriptor> members = service.describeGroupMembers(
                new UserId(1L),
                Arrays.asList(
                        groupMember(groupId, 2L, "group-bob"),
                        groupMember(groupId, 3L, "group-carol"),
                        groupMember(groupId, 4L)));

        assertThat(members)
                .extracting(MemberDescriptor::getDisplayName)
                .extracting(MemberDisplayName::getValue)
                .containsExactly("friend-bob", "group-carol", "dave");
    }

    @Test
    void getUserChatListSkipsDismissedGroupChats() {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha"));
        Group dismissedGroup = group(1002L, 1L, "beta");
        dismissedGroup.setStatus(GroupStatus.DISMISSED);
        groupRepository.groups.add(dismissedGroup);
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1002L, 1L));
        ImChatService service = new ImChatService(
                null, new EmptyFriendRepository(), new EmptyPrivateChatRepository(),
                groupRepository, groupChatRepository, null);

        List<ImUserChatDescriptor> descriptors = service.getUserChatList(new UserId(1L));

        assertThat(descriptors)
                .extracting(descriptor -> descriptor.getChatId().getValue())
                .containsExactly(101L);
        assertThat(descriptors)
                .extracting(descriptor -> descriptor.getChatName().getValue())
                .containsExactly("alpha");
    }

    @Test
    void getUserChatListOnlyReturnsNormalChatsByActiveTime() {
        MemoryFriendRepository friendRepository = new MemoryFriendRepository();
        friendRepository.friends.add(friend(1L, 2L, "bob", null));
        friendRepository.friends.add(friend(1L, 3L, "carol", null));
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        ImPrivateChat olderPrivateChat = privateChat(101L, 1L, 2L, LocalDateTime.of(2026, 1, 1, 10, 0));
        ImPrivateChat hiddenPrivateChat = privateChat(102L, 1L, 3L, LocalDateTime.of(2026, 1, 1, 12, 0));
        hiddenPrivateChat.hide();
        privateChatRepository.privateChats.add(olderPrivateChat);
        privateChatRepository.privateChats.add(hiddenPrivateChat);
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha"));
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        ImGroupChat newerGroupChat = groupChat(201L, 1001L, 1L);
        newerGroupChat.setActiveTime(LocalDateTime.of(2026, 1, 1, 11, 0));
        groupChatRepository.groupChats.add(newerGroupChat);
        ImChatService service = new ImChatService(
                null, friendRepository, privateChatRepository,
                groupRepository, groupChatRepository, null);

        List<ImUserChatDescriptor> descriptors = service.getUserChatList(new UserId(1L));

        assertThat(descriptors)
                .extracting(descriptor -> descriptor.getChatId().getValue())
                .containsExactly(201L, 101L);
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

    private GroupMember groupMember(GroupId groupId, Long userId) {
        return groupMember(groupId, userId, null);
    }

    private GroupMember groupMember(GroupId groupId, Long userId, String userAlias) {
        UserId memberUserId = new UserId(userId);
        return GroupMember.builder()
                .id(new MemberId(groupId, memberUserId))
                .groupId(groupId)
                .userId(memberUserId)
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

    private ImGroupChat groupChat(Long chatId, Long groupId, Long userId) {
        return ImGroupChat.builder()
                .id(new ImChatId(chatId))
                .type(ImChatType.GROUP)
                .groupId(new GroupId(groupId))
                .userId(new UserId(userId))
                .unreadMessageCount(0)
                .build();
    }

    private ImPrivateChat privateChat(Long chatId, Long userId, Long peerUserId, LocalDateTime activeTime) {
        return ImPrivateChat.builder()
                .id(new ImChatId(chatId))
                .type(ImChatType.PRIVATE)
                .userId(new UserId(userId))
                .peerUserId(new UserId(peerUserId))
                .status(ImChatStatus.NORMAL)
                .activeTime(activeTime)
                .build();
    }

    private static class MemoryGroupChatRepository implements com.co.kc.imchat.domain.chat.ImGroupChatRepository {
        private final List<ImGroupChat> groupChats = new java.util.ArrayList<>();

        @Override
        public Optional<ImGroupChat> find(ImChatId chatId) {
            return Optional.empty();
        }

        @Override
        public Optional<ImGroupChat> find(GroupId groupId, UserId userId) {
            return Optional.empty();
        }

        @Override
        public List<ImGroupChat> find(GroupId groupId) {
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupChat> find(java.util.Collection<GroupId> groupIds) {
            return groupChats.stream()
                    .filter(chat -> groupIds.contains(chat.getGroupId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(UserId userId, java.util.Collection<GroupId> groupIds) {
            return groupChats.stream()
                    .filter(chat -> chat.getUserId().equals(userId))
                    .filter(chat -> groupIds.contains(chat.getGroupId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(UserId userId) {
            return groupChats.stream()
                    .filter(chat -> chat.getUserId().equals(userId))
                    .collect(java.util.stream.Collectors.toList());
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
        }

        @Override
        public void saveAll(List<ImGroupChat> groupChats) {
        }

        @Override
        public boolean contain(ImChatId chatId, UserId userId) {
            return false;
        }
    }

    private static class MemoryGroupRepository implements GroupRepository {
        private final List<Group> groups = new java.util.ArrayList<>();

        @Override
        public Optional<Group> find(GroupId groupId) {
            return groups.stream()
                    .filter(group -> group.getId().equals(groupId))
                    .findFirst();
        }

        @Override
        public List<Group> find(UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public List<Group> find(List<GroupId> groupIds) {
            return groups.stream()
                    .filter(group -> groupIds.contains(group.getId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public void save(Group group) {
        }
    }

    private static class EmptyPrivateChatRepository implements com.co.kc.imchat.domain.chat.ImPrivateChatRepository {
        @Override
        public Optional<com.co.kc.imchat.domain.chat.ImPrivateChat> find(ImChatId chatId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.co.kc.imchat.domain.chat.ImPrivateChat> find(UserId userId, UserId friendUserId) {
            return Optional.empty();
        }

        @Override
        public List<com.co.kc.imchat.domain.chat.ImPrivateChat> find(UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public void save(com.co.kc.imchat.domain.chat.ImPrivateChat imPrivateChat) {
        }

        @Override
        public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId userId) {
            return Collections.emptyList();
        }
    }

    private static class MemoryPrivateChatRepository extends EmptyPrivateChatRepository {
        private final List<ImPrivateChat> privateChats = new java.util.ArrayList<>();

        @Override
        public List<ImPrivateChat> find(UserId userId) {
            return privateChats.stream()
                    .filter(chat -> chat.getUserId().equals(userId))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    private static class EmptyFriendRepository implements com.co.kc.imchat.domain.friend.FriendRepository {
        @Override
        public List<com.co.kc.imchat.domain.friend.Friend> find(UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public Optional<com.co.kc.imchat.domain.friend.Friend> find(UserId userId, UserId friendUserId) {
            return Optional.empty();
        }

        @Override
        public boolean contain(UserId userId, UserId friendUserId) {
            return false;
        }

        @Override
        public List<com.co.kc.imchat.domain.friend.Friend> find(UserId userId, List<UserId> friendUserIds) {
            return Collections.emptyList();
        }

        @Override
        public void save(com.co.kc.imchat.domain.friend.Friend friend) {
        }

        @Override
        public void remove(UserId userId, UserId friendUserId) {
        }
    }

    private static class MemoryFriendRepository implements FriendRepository {
        private final List<Friend> friends = new java.util.ArrayList<>();

        @Override
        public List<Friend> find(UserId userId) {
            return friends.stream()
                    .filter(friend -> friend.getUserId().equals(userId))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public Optional<Friend> find(UserId userId, UserId friendUserId) {
            return find(userId).stream()
                    .filter(friend -> friend.getFriendUserId().equals(friendUserId))
                    .findFirst();
        }

        @Override
        public boolean contain(UserId userId, UserId friendUserId) {
            return find(userId, friendUserId).isPresent();
        }

        @Override
        public List<Friend> find(UserId userId, List<UserId> friendUserIds) {
            return find(userId).stream()
                    .filter(friend -> friendUserIds.contains(friend.getFriendUserId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public void save(Friend friend) {
        }

        @Override
        public void remove(UserId userId, UserId friendUserId) {
        }
    }

    private static class MemoryUserRepository implements UserRepository {
        private final List<User> users = new java.util.ArrayList<>();

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
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public Optional<User> find(com.co.kc.imchat.domain.user.UserEmail email) {
            return Optional.empty();
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
