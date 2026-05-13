package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.group.ImGroup;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.group.ImGroupId;
import com.co.kc.imchat.domain.group.ImGroupMember;
import com.co.kc.imchat.domain.group.ImGroupMemberId;
import com.co.kc.imchat.domain.group.ImGroupMemberRepository;
import com.co.kc.imchat.domain.group.ImGroupName;
import com.co.kc.imchat.domain.group.ImGroupRepository;
import com.co.kc.imchat.domain.group.ImGroupService;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupCreateCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupInviteMembersCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupCreateDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupDetailDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupItemDTO;
import com.co.kc.imchat.model.cqrs.query.ImGroupDetailQuery;
import com.co.kc.imchat.model.cqrs.query.ImGroupListQuery;
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
        RecordingGroupRepository groupRepository = new RecordingGroupRepository();
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        RecordingGroupInboxMessageRepository groupInboxMessageRepository = new RecordingGroupInboxMessageRepository();
        SignedInSessionRepository sessionRepository = new SignedInSessionRepository();
        FixedSnowflakeId snowflakeId = new FixedSnowflakeId(1000L);
        GroupAppService appService = new GroupAppService(
                snowflakeId,
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                new ImGroupService(groupMemberRepository, groupChatRepository),
                new ImChatService(snowflakeId, null, null, null, null, sessionRepository),
                groupInboxMessageRepository,
                new ImMessageService(snowflakeId),
                new NoopDomainEventPublisher());
        ImGroupCreateCmd command = new ImGroupCreateCmd(1L, Collections.singletonList(2L), "group");

        ImGroupCreateDTO result = appService.createGroup(command);

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
        assertThat(sessionRepository.session.getChatId().getValue()).isEqualTo(1001L);
    }

    @Test
    void createGroupStoresSystemMessageForEachMemberAndUpdatesChats() {
        RecordingGroupRepository groupRepository = new RecordingGroupRepository();
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        RecordingGroupInboxMessageRepository groupInboxMessageRepository = new RecordingGroupInboxMessageRepository();
        FixedSnowflakeId snowflakeId = new FixedSnowflakeId(1000L);
        GroupAppService appService = new GroupAppService(
                snowflakeId,
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                new ImGroupService(groupMemberRepository, groupChatRepository),
                new ImChatService(snowflakeId, null, null, null, null, new SignedInSessionRepository()),
                groupInboxMessageRepository,
                new ImMessageService(snowflakeId),
                new NoopDomainEventPublisher());
        ImGroupCreateCmd command = new ImGroupCreateCmd(1L, Collections.singletonList(2L), "group");

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
        assertThat(groupInboxMessageRepository.savedMessages.get(0).getStatus()).isEqualTo(ImGroupMessageStatus.READ);
        assertThat(groupInboxMessageRepository.savedMessages.get(1).getStatus()).isEqualTo(ImGroupMessageStatus.RECEIVED);
        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getLastMessageId().getValue())
                .containsExactly(1003L, 1003L);
        assertThat(groupChatRepository.savedGroupChats)
                .extracting(ImGroupChat::getUnreadMessageCount)
                .containsExactly(0, 1);
    }

    @Test
    void inviteGroupMembersCreatesMemberAndChatRowsForNewMembers() {
        RecordingGroupRepository groupRepository = new RecordingGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "group"));
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        GroupAppService appService = new GroupAppService(
                new FixedSnowflakeId(3000L),
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                new ImGroupService(groupMemberRepository, groupChatRepository),
                new ImChatService(new FixedSnowflakeId(3000L), null, null, null, null, null),
                null,
                null,
                null);
        ImGroupInviteMembersCmd command = new ImGroupInviteMembersCmd(1L, 1001L, Collections.singletonList(2L));

        appService.inviteGroupMembers(command);

        assertThat(groupMemberRepository.savedMembers)
                .extracting(member -> member.getUserId().getValue())
                .containsExactly(2L);
        assertThat(groupChatRepository.savedGroupChats)
                .extracting(chat -> chat.getUserId().getValue())
                .containsExactly(2L);
    }

    @Test
    void getGroupListReturnsGroupsJoinedByUser() {
        RecordingGroupRepository groupRepository = new RecordingGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha"));
        groupRepository.groups.add(group(1002L, 2L, "beta"));
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L, 901L, 2));
        groupChatRepository.groupChats.add(groupChat(102L, 1002L, 1L, null, 0));
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        groupMemberRepository.members.add(groupMember(1002L, 1L));
        GroupAppService appService = new GroupAppService(
                null,
                groupRepository,
                groupChatRepository,
                groupMemberRepository,
                new ImGroupService(groupMemberRepository, groupChatRepository),
                null,
                null,
                null,
                null);

        List<ImGroupItemDTO> groupList = appService.getGroupList(new ImGroupListQuery(1L));

        assertThat(groupList)
                .extracting(ImGroupItemDTO::getGroupName)
                .containsExactly("alpha", "beta");
        assertThat(groupList)
                .extracting(ImGroupItemDTO::getChatId)
                .containsExactly(101L, 102L);
        assertThat(groupList)
                .extracting(ImGroupItemDTO::getUnreadMessageCount)
                .containsExactly(2, 0);
        assertThat(groupRepository.findByUserIdCount).isEqualTo(1);
    }

    @Test
    void getGroupDetailReturnsGroupAndMembersForMember() {
        RecordingGroupRepository groupRepository = new RecordingGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha"));
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
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

        ImGroupDetailDTO detail = appService.getGroupDetail(new ImGroupDetailQuery(1L, 1001L));

        assertThat(detail.getGroupId()).isEqualTo(1001L);
        assertThat(detail.getChatId()).isEqualTo(101L);
        assertThat(detail.getGroupName()).isEqualTo("alpha");
        assertThat(detail.getMemberCount()).isEqualTo(2);
        assertThat(detail.getMembers())
                .extracting(ImGroupDetailDTO.Member::getUserId)
                .containsExactly(1L, 2L);
    }

    @Test
    void getGroupDetailRejectsUserOutsideGroup() {
        RecordingGroupRepository groupRepository = new RecordingGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "alpha"));
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
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

        assertThatThrownBy(() -> appService.getGroupDetail(new ImGroupDetailQuery(2L, 1001L)))
                .isInstanceOf(BusinessException.class);
    }

    private ImGroupChat groupChat(Long chatId, Long groupId, Long userId) {
        return groupChat(chatId, groupId, userId, null, 0);
    }

    private ImGroupChat groupChat(Long chatId, Long groupId, Long userId, Long lastMessageId, int unreadMessageCount) {
        return ImGroupChat.builder()
                .id(new ImChatId(chatId))
                .groupId(new ImGroupId(groupId))
                .userId(new UserId(userId))
                .type(ImChatType.GROUP)
                .lastMessageId(lastMessageId == null ? null : new com.co.kc.imchat.domain.message.ImMessageId(lastMessageId))
                .unreadMessageCount(unreadMessageCount)
                .build();
    }

    private ImGroupMember groupMember(Long groupId, Long userId) {
        ImGroupId imGroupId = new ImGroupId(groupId);
        UserId imUserId = new UserId(userId);
        return ImGroupMember.builder()
                .id(new ImGroupMemberId(imGroupId, imUserId))
                .groupId(imGroupId)
                .userId(imUserId)
                .joinTime(java.time.LocalDateTime.now())
                .build();
    }

    private ImGroup group(Long groupId, Long ownerId, String name) {
        return ImGroup.builder()
                .id(new ImGroupId(groupId))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(ownerId))
                .name(new ImGroupName(name))
                .build();
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

    private static class RecordingGroupRepository implements ImGroupRepository {
        private final List<ImGroup> groups = new ArrayList<>();
        private ImGroup savedGroup;
        private int findByUserIdCount;

        @Override
        public ImGroup find(ImGroupId groupId) {
            if (savedGroup != null && savedGroup.getId().equals(groupId)) {
                return savedGroup;
            }
            return groups.stream()
                    .filter(group -> group.getId().equals(groupId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<ImGroup> find(List<ImGroupId> groupIds) {
            List<ImGroup> result = groups.stream()
                    .filter(group -> groupIds.contains(group.getId()))
                    .collect(Collectors.toList());
            if (savedGroup != null && groupIds.contains(savedGroup.getId())) {
                result.add(savedGroup);
            }
            return result;
        }

        @Override
        public List<ImGroup> find(UserId userId) {
            findByUserIdCount++;
            return groups;
        }

        @Override
        public void save(ImGroup group) {
            savedGroup = group;
        }
    }

    private static class RecordingGroupChatRepository implements ImGroupChatRepository {
        private final List<ImGroupChat> groupChats = new ArrayList<>();
        private List<ImGroupChat> savedGroupChats = new ArrayList<>();

        @Override
        public ImGroupChat find(ImChatId chatId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getId().equals(chatId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public ImGroupChat find(ImGroupId groupId, UserId userId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getGroupId().equals(groupId))
                    .filter(groupChat -> groupChat.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<ImGroupChat> find(ImGroupId groupId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getGroupId().equals(groupId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(java.util.Collection<ImGroupId> groupIds) {
            return groupChats.stream()
                    .filter(groupChat -> groupIds.contains(groupChat.getGroupId()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(UserId userId, java.util.Collection<ImGroupId> groupIds) {
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
        public List<ImGroupChat> findByUserIdsAndGroupId(ImGroupId groupId, List<UserId> userIds) {
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
            savedGroupChats = new ArrayList<>(groupChats);
        }

        @Override
        public boolean contain(ImChatId chatId, UserId userId) {
            return find(chatId) != null && find(chatId).contain(userId);
        }
    }

    private static class RecordingGroupMemberRepository implements ImGroupMemberRepository {
        private final List<ImGroupMember> members = new ArrayList<>();
        private List<ImGroupMember> savedMembers = new ArrayList<>();
        private int findByGroupIdCount;
        private int countByGroupIdsCount;

        @Override
        public List<ImGroupMember> find(ImGroupId groupId) {
            findByGroupIdCount++;
            return members.stream()
                    .filter(member -> member.getGroupId().equals(groupId))
                    .collect(Collectors.toList());
        }

        @Override
        public ImGroupMember find(ImGroupId groupId, UserId userId) {
            return members.stream()
                    .filter(member -> member.getGroupId().equals(groupId))
                    .filter(member -> member.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public void saveAll(List<ImGroupMember> members) {
            savedMembers = new ArrayList<>(members);
            this.members.addAll(members);
        }

        @Override
        public Map<ImGroupId, Integer> countByGroupIds(List<ImGroupId> groupIds) {
            countByGroupIdsCount++;
            return members.stream()
                    .filter(member -> groupIds.contains(member.getGroupId()))
                    .collect(Collectors.groupingBy(ImGroupMember::getGroupId, Collectors.summingInt(member -> 1)));
        }
    }

    private static class RecordingGroupInboxMessageRepository implements ImGroupInboxMessageRepository {
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
        public List<ImGroupInboxMessage> findByGroupIdAndMessageId(ImGroupId groupId, ImMessageId messageId) {
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupInboxMessage> findUnreadMessages(ImChatId chatId, UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupInboxMessage> queryHistory(ImChatId chatId, UserId userId, ImMessageId lastMessageId, Integer count) {
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
}
