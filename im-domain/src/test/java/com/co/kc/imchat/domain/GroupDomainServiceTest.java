package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.domain.chat.service.ImChatService;
import com.co.kc.imchat.domain.chat.model.ImChatStatus;
import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.domain.chat.model.GroupChatMembership;
import com.co.kc.imchat.domain.chat.model.GroupChatJoin;
import com.co.kc.imchat.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.domain.chat.model.ImUserChatDescriptor;
import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.model.FriendAlias;
import com.co.kc.imchat.domain.friend.model.FriendEdge;
import com.co.kc.imchat.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.domain.friend.model.FriendStatus;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.model.GroupName;
import com.co.kc.imchat.domain.group.model.GroupCreation;
import com.co.kc.imchat.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.domain.group.repository.GroupRepository;
import com.co.kc.imchat.domain.group.model.GroupMembership;
import com.co.kc.imchat.domain.group.service.GroupService;
import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.group.model.GroupStatus;
import com.co.kc.imchat.domain.group.model.GroupUserAlias;
import com.co.kc.imchat.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.domain.group.model.MemberDisplayName;
import com.co.kc.imchat.domain.group.model.MemberId;
import com.co.kc.imchat.domain.group.model.MemberCount;
import com.co.kc.imchat.domain.group.model.UserGroupDescriptor;
import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.message.model.ImMessage;
import com.co.kc.imchat.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.model.ImMessageContent;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.message.model.ImMessageToken;
import com.co.kc.imchat.domain.message.model.ImMessageType;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
import com.co.kc.imchat.domain.user.repository.UserRepository;
import com.co.kc.imchat.domain.user.model.UserEmail;
import com.co.kc.imchat.common.identity.snowflake.ISnowflakeMachineId;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupDomainServiceTest {

    @Test
    void createMembershipCreatesMembersWithOwnerIncluded() {
        GroupService service = new GroupService(null, null, null, null, null);
        GroupId groupId = new GroupId(1001L);

        GroupMembership membership = service.createMembership(
                groupId,
                new UserId(1L),
                Arrays.asList(new UserId(2L), new UserId(1L)));

        assertThat(membership.getMembers())
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(1L, 2L);
        assertThat(membership.getMembers())
                .allSatisfy(member -> assertThat(member.getJoinTime()).isNotNull());
    }

    @Test
    void createMembershipAllowsEmptyMemberIdsAndOnlyKeepsOwner() {
        GroupService service = new GroupService(null, null, null, null, null);
        GroupId groupId = new GroupId(1001L);

        GroupMembership membership = service.createMembership(groupId, new UserId(1L), null);

        assertThat(membership.getMembers())
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(1L);
    }

    @Test
    void createGroupCreatesGroupAndMembersTogether() {
        GroupService service = new GroupService(null, null, null, null, null);
        GroupId groupId = new GroupId(1001L);
        UserId ownerId = new UserId(1L);

        GroupCreation creation = service.createGroup(
                groupId,
                ownerId,
                new GroupName("group"),
                Arrays.asList(new UserId(2L), ownerId));

        assertThat(creation.getGroup().getId()).isEqualTo(groupId);
        assertThat(creation.getGroup().getOwnerId()).isEqualTo(ownerId);
        assertThat(creation.getGroup().getName().getValue()).isEqualTo("group");
        assertThat(creation.getGroup().getMemberCount().getValue()).isEqualTo(2);
        assertThat(creation.getMembers())
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(1L, 2L);
    }

    @Test
    void groupMembershipInvitesOnlyNewMembers() {
        GroupId groupId = new GroupId(1001L);
        GroupMembership membership = new GroupMembership(
                groupId,
                Collections.singletonList(groupMember(groupId, 1L)));

        List<GroupMember> members = membership.invite(
                new UserId(1L),
                Arrays.asList(new UserId(1L), new UserId(2L), new UserId(2L)));

        assertThat(members)
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(2L);
    }

    @Test
    void groupMembershipRejectsInviterOutsideGroup() {
        GroupId groupId = new GroupId(1001L);
        GroupMembership membership = new GroupMembership(
                groupId,
                Collections.singletonList(groupMember(groupId, 1L)));

        assertThatThrownBy(() -> membership.invite(new UserId(2L), Collections.singletonList(new UserId(3L))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void groupChatMembershipFindsOwnerChat() {
        ImGroupChat ownerChat = groupChat(101L, 1001L, 1L);
        ImGroupChat memberChat = groupChat(102L, 1001L, 2L);
        GroupChatMembership membership = new GroupChatMembership(
                new GroupId(1001L),
                Arrays.asList(memberChat, ownerChat));

        Optional<ImGroupChat> result = membership.findOwnerChat(new UserId(1L));

        assertThat(result).contains(ownerChat);
    }

    @Test
    void groupChatMembershipReturnsEmptyWhenOwnerChatMissing() {
        GroupChatMembership membership = new GroupChatMembership(
                new GroupId(1001L),
                Collections.singletonList(groupChat(102L, 1001L, 2L)));

        Optional<ImGroupChat> result = membership.findOwnerChat(new UserId(1L));

        assertThat(result).isEmpty();
    }

    @Test
    void groupChatMembershipFindsMemberChat() {
        GroupChatMembership membership = new GroupChatMembership(
                new GroupId(1001L),
                Arrays.asList(groupChat(101L, 1001L, 1L), groupChat(102L, 1001L, 2L)));

        Optional<ImGroupChat> result = membership.findMemberChat(new UserId(2L));

        assertThat(result).isPresent();
        assertThat(result.get().getId().getValue()).isEqualTo(102L);
    }

    @Test
    void createGroupChatMembershipCreatesChatForEachMember() {
        ImChatService service = new ImChatService(null, null, null, null, null, new FixedSnowflakeId(101L));
        GroupId groupId = new GroupId(1001L);

        GroupChatMembership membership = service.createGroupChatMembership(
                Arrays.asList(groupMember(groupId, 1L), groupMember(groupId, 2L)));

        assertThat(membership.getGroupId()).isEqualTo(groupId);
        assertThat(membership.getChats())
                .extracting(chat -> chat.getId().getValue())
                .containsExactly(101L, 102L);
        assertThat(membership.getChats())
                .extracting(chat -> chat.getUserId().getValue())
                .containsExactly(1L, 2L);
    }

    @Test
    void findGroupChatMembershipLoadsGroupChatsFromRepository() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        ImChatService service = new ImChatService(null, null, null, groupChatRepository, null, null);

        GroupChatMembership membership = service.findGroupChatMembership(new GroupId(1001L));

        assertThat(membership.getGroupId().getValue()).isEqualTo(1001L);
        assertThat(membership.getChats())
                .extracting(chat -> chat.getId().getValue())
                .containsExactly(101L, 102L);
    }

    @Test
    void joinGroupChatReturnsNewGroupChatsAndMergedGroupChats() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        ImChatService service = new ImChatService(
                null, null, null, groupChatRepository, null, new FixedSnowflakeId(102L));
        GroupId groupId = new GroupId(1001L);

        GroupChatJoin join = service.joinGroupChat(groupId, Collections.singletonList(groupMember(groupId, 2L)));

        assertThat(join.getNewGroupChats())
                .extracting(chat -> chat.getId().getValue())
                .containsExactly(102L);
        assertThat(join.getGroupChats())
                .extracting(chat -> chat.getUserId().getValue())
                .containsExactly(1L, 2L);
    }

    @Test
    void describeUserGroupsCombinesGroupsWithUserGroupChats() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        GroupService service = new GroupService(groupMemberRepository, groupChatRepository, null, null, null);
        Group alpha = group(1001L, 1L, "alpha", new MemberCount(2));
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
        assertThat(descriptors)
                .extracting(UserGroupDescriptor::getMemberCount)
                .extracting(MemberCount::getValue)
                .containsExactly(2, 1);
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
        GroupService service = new GroupService(null, null, null, friendRepository, userRepository);

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
        groupRepository.groups.add(group(1002L, 1L, "beta", GroupStatus.DISMISSED));
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1002L, 1L));
        ImChatService service = new ImChatService(
                new EmptyFriendRepository(), new EmptyPrivateChatRepository(),
                groupRepository, groupChatRepository, null, null);

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
                friendRepository, privateChatRepository,
                groupRepository, groupChatRepository, null, null);

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
        friend.setStatus(FriendStatus.NORMAL);
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

    private Group group(Long groupId, Long ownerId, String name, GroupStatus status) {
        return group(groupId, ownerId, name, new MemberCount(1), status);
    }

    private Group group(Long groupId, Long ownerId, String name, MemberCount memberCount, GroupStatus status) {
        return Group.builder()
                .id(new GroupId(groupId))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(ownerId))
                .name(new GroupName(name))
                .memberCount(memberCount)
                .status(status)
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

    private ImGroupInboxMessage groupInboxMessage(
            Long messageId, Long chatId, Long groupId, Long userId, Long senderId) {
        return ImGroupInboxMessage.builder()
                .id(new ImMessageId(messageId))
                .token(new ImMessageToken("token-" + messageId))
                .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                .chatId(new ImChatId(chatId))
                .groupId(new GroupId(groupId))
                .userId(new UserId(userId))
                .senderId(new UserId(senderId))
                .status(ImGroupMessageStatus.SENT)
                .sendTime(LocalDateTime.now())
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

    private static class MemoryGroupChatRepository implements ImGroupChatRepository {
        private final List<ImGroupChat> groupChats = new java.util.ArrayList<>();

        @Override
        public Optional<ImGroupChat> find(ImChatId chatId) {
            return groupChats.stream()
                    .filter(chat -> chat.getId().equals(chatId))
                    .findFirst();
        }

        @Override
        public Optional<ImGroupChat> find(GroupId groupId, UserId userId) {
            return groupChats.stream()
                    .filter(chat -> chat.getGroupId().equals(groupId))
                    .filter(chat -> chat.getUserId().equals(userId))
                    .findFirst();
        }

        @Override
        public List<ImGroupChat> find(GroupId groupId) {
            return groupChats.stream()
                    .filter(chat -> chat.getGroupId().equals(groupId))
                    .collect(java.util.stream.Collectors.toList());
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
        public List<ImGroupChat> find(GroupId groupId, List<UserId> memberIds) {
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
        public void save(List<ImGroupChat> groupChats) {
        }

        @Override
        public boolean contain(ImChatId chatId, UserId userId) {
            return false;
        }

        @Override
        public void remove(GroupId groupId, UserId userId) {
            groupChats.removeIf(chat -> chat.getGroupId().equals(groupId)
                    && chat.getUserId().equals(userId));
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

    private static class EmptyPrivateChatRepository implements ImPrivateChatRepository {
        @Override
        public Optional<ImPrivateChat> find(ImChatId chatId) {
            return Optional.empty();
        }

        @Override
        public Optional<ImPrivateChat> find(UserId userId, UserId friendUserId) {
            return Optional.empty();
        }

        @Override
        public boolean contain(UserId userId, UserId peerUserId) {
            return false;
        }

        @Override
        public List<ImPrivateChat> find(UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public void save(ImPrivateChat imPrivateChat) {
        }

        @Override
        public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public void remove(UserId userId, UserId peerUserId) {
        }
    }

    private static class MemoryPrivateChatRepository extends EmptyPrivateChatRepository {
        private final List<ImPrivateChat> privateChats = new java.util.ArrayList<>();

        @Override
        public Optional<ImPrivateChat> find(ImChatId chatId) {
            return privateChats.stream()
                    .filter(chat -> chat.getId().equals(chatId))
                    .findFirst();
        }

        @Override
        public Optional<ImPrivateChat> find(UserId userId, UserId friendUserId) {
            return privateChats.stream()
                    .filter(chat -> chat.getUserId().equals(userId))
                    .filter(chat -> chat.getPeerUserId().equals(friendUserId))
                    .findFirst();
        }

        @Override
        public boolean contain(UserId userId, UserId peerUserId) {
            return privateChats.stream()
                    .anyMatch(chat -> chat.getUserId().equals(userId)
                            && chat.getPeerUserId().equals(peerUserId));
        }

        @Override
        public List<ImPrivateChat> find(UserId userId) {
            return privateChats.stream()
                    .filter(chat -> chat.getUserId().equals(userId))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    private ImPrivateChat privateChat(Long chatId, Long userId, Long peerUserId) {
        return ImPrivateChat.builder()
                .id(new ImChatId(chatId))
                .type(ImChatType.PRIVATE)
                .userId(new UserId(userId))
                .peerUserId(new UserId(peerUserId))
                .build();
    }

    private static class MemoryGroupMemberRepository implements GroupMemberRepository {
        private final List<GroupMember> members = new java.util.ArrayList<>();

        @Override
        public List<GroupMember> find(GroupId groupId) {
            return members.stream()
                    .filter(member -> member.getGroupId().equals(groupId))
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<GroupMember> find(GroupId groupId, UserId userId) {
            return find(groupId).stream()
                    .filter(member -> member.getUserId().equals(userId))
                    .findFirst();
        }

        @Override
        public boolean contain(GroupId groupId, UserId userId) {
            return find(groupId, userId).isPresent();
        }

        @Override
        public void save(List<GroupMember> members) {
            this.members.addAll(members);
        }

        @Override
        public void save(GroupMember member) {
            this.members.add(member);
        }

        @Override
        public void remove(GroupMember groupMember) {
            members.removeIf(member -> member.getGroupId().equals(groupMember.getGroupId())
                    && member.getUserId().equals(groupMember.getUserId()));
        }
    }

    private static class MemoryGroupInboxMessageRepository implements ImGroupInboxMessageRepository {
        private final List<ImGroupInboxMessage> unreadMessages = new java.util.ArrayList<>();

        @Override
        public void save(ImGroupInboxMessage message) {
        }

        @Override
        public void save(List<ImGroupInboxMessage> messages) {
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
            return unreadMessages.stream()
                    .filter(message -> message.getChatId().equals(chatId))
                    .filter(message -> message.getUserId().equals(userId))
                    .collect(Collectors.toList());
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

    private static class EmptyFriendRepository implements FriendRepository {
        @Override
        public List<Friend> find(UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public Optional<Friend> find(FriendEdge edge) {
            return Optional.empty();
        }

        @Override
        public boolean contain(UserId userId, UserId friendUserId) {
            return false;
        }

        @Override
        public List<Friend> find(UserId userId, List<UserId> friendUserIds) {
            return Collections.emptyList();
        }

        @Override
        public void save(Friend friend) {
        }

        @Override
        public boolean isFriendshipActive(UserId userId, UserId friendUserId) {
            return false;
        }

        @Override
        public void remove(Friend friend) {
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
        public Optional<Friend> find(FriendEdge edge) {
            return find(edge.getUserId()).stream()
                    .filter(friend -> friend.getFriendUserId().equals(edge.getFriendUserId()))
                    .findFirst();
        }

        @Override
        public boolean contain(UserId userId, UserId friendUserId) {
            return find(new FriendEdge(userId, friendUserId)).isPresent();
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
        public boolean isFriendshipActive(UserId userId, UserId friendUserId) {
            return find(new FriendEdge(userId, friendUserId))
                    .map(Friend::isNormal)
                    .orElse(false);
        }

        @Override
        public void remove(Friend friend) {
            friends.removeIf(savedFriend -> savedFriend.getUserId().equals(friend.getUserId())
                    && savedFriend.getFriendUserId().equals(friend.getFriendUserId()));
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
