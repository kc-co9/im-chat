package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.group.ImGroupId;
import com.co.kc.imchat.domain.group.ImGroupMember;
import com.co.kc.imchat.domain.group.ImGroupMemberId;
import com.co.kc.imchat.domain.group.ImGroupMemberRepository;
import com.co.kc.imchat.domain.group.ImGroup;
import com.co.kc.imchat.domain.group.ImGroupName;
import com.co.kc.imchat.domain.group.ImGroupService;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImGroupMessageSentEvent;
import com.co.kc.imchat.domain.message.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.message.ImMessageContent;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserService;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupChatOpenCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatOpenDTO;
import com.co.kc.imchat.model.cqrs.command.im.ImGroupMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImGroupMessageSendCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImGroupSentNotifyCmd;
import com.co.kc.imchat.model.cqrs.query.ImGroupMessageDetailQuery;
import com.co.kc.imchat.support.auth.PasswordService;
import com.co.kc.imchat.support.event.DomainEventPublisher;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.support.notifier.ImMessageNotifierInvoker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupChatAppServiceTest {

    @Test
    void sendGroupMessageRejectsChatIdOwnedByAnotherMember() {
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        RecordingGroupInboxRepository inboxRepository = new RecordingGroupInboxRepository();
        GroupMessageAppService appService = new GroupMessageAppService(
                new FixedSnowflakeId(900L),
                groupChatRepository,
                groupMemberRepository,
                inboxRepository,
                new NonChattingUserService(),
                new ImGroupService(groupMemberRepository, groupChatRepository),
                new ImMessageService(new FixedSnowflakeId(1L)),
                null,
                new NoopDomainEventPublisher());

        ImGroupMessageSendCmd command = new ImGroupMessageSendCmd();
        command.setChatId(102L);
        command.setSenderId(1L);
        command.setMessageToken("token-1");
        command.setMessageType(ImMessageType.TEXT);
        command.setMessageContent("hello");

        assertThatThrownBy(() -> appService.sendMessage(command))
                .isInstanceOf(BusinessException.class);
        assertThat(inboxRepository.savedMessages).isEmpty();
        assertThat(groupChatRepository.savedGroupChats).isEmpty();
    }

    @Test
    void openGroupChatEntersExistingChatByChatId() {
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        SignedInSessionRepository sessionRepository = new SignedInSessionRepository();
        ChatAppService appService = new ChatAppService(
                new FixedSnowflakeId(2000L),
                null,
                groupChatRepository,
                groupMemberRepository,
                new RecordingGroupInboxRepository(),
                null,
                new ImChatService(null, null, null, null, null, sessionRepository));
        ImGroupChatOpenCmd command = new ImGroupChatOpenCmd(2L, 102L);

        ImChatOpenDTO result = appService.openGroupChat(command);

        assertThat(result.getChatId()).isEqualTo(102L);
        assertThat(groupChatRepository.savedGroupChats).hasSize(1);
        ImGroupChat savedChat = groupChatRepository.findSavedByUserId(2L);
        assertThat(savedChat.getId().getValue()).isEqualTo(102L);
        assertThat(savedChat.getGroupId().getValue()).isEqualTo(1001L);
        assertThat(savedChat.getUnreadMessageCount()).isZero();
        assertThat(sessionRepository.session.getChatId().getValue()).isEqualTo(102L);
    }

    @Test
    void sendGroupMessageCreatesInboxForEachMemberAndUpdatesReadState() {
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        RecordingGroupInboxRepository inboxRepository = new RecordingGroupInboxRepository();
        GroupMessageAppService appService = new GroupMessageAppService(
                new FixedSnowflakeId(900L),
                groupChatRepository,
                groupMemberRepository,
                inboxRepository,
                new NonChattingUserService(),
                new ImGroupService(groupMemberRepository, groupChatRepository),
                new ImMessageService(new FixedSnowflakeId(1L)),
                null,
                new NoopDomainEventPublisher());

        ImGroupMessageSendCmd command = new ImGroupMessageSendCmd();
        command.setChatId(101L);
        command.setSenderId(1L);
        command.setMessageToken("token-1");
        command.setMessageType(ImMessageType.TEXT);
        command.setMessageContent("hello");

        appService.sendMessage(command);

        assertThat(inboxRepository.savedMessages).hasSize(2);
        ImGroupInboxMessage senderMessage = inboxRepository.findSavedByUserId(1L);
        ImGroupInboxMessage receiverMessage = inboxRepository.findSavedByUserId(2L);
        assertThat(senderMessage.getStatus()).isEqualTo(ImGroupMessageStatus.READ);
        assertThat(senderMessage.getReadTime()).isNotNull();
        assertThat(senderMessage.getReceivedTime()).isNull();
        assertThat(receiverMessage.getStatus()).isEqualTo(ImGroupMessageStatus.RECEIVED);
        assertThat(receiverMessage.getReceivedTime()).isNotNull();
        assertThat(receiverMessage.getChatId().getValue()).isEqualTo(102L);
        assertThat(groupChatRepository.findSavedByUserId(1L).getUnreadMessageCount()).isZero();
        assertThat(groupChatRepository.findSavedByUserId(1L).getReadMessageId().getValue()).isEqualTo(900L);
        assertThat(groupChatRepository.findSavedByUserId(2L).getUnreadMessageCount()).isEqualTo(1);
        assertThat(groupChatRepository.findSavedByUserId(2L).getReadMessageId()).isNull();
    }

    @Test
    void groupMessageSentNotificationSkipsSender() {
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        RecordingNotifierInvoker notifierInvoker = new RecordingNotifierInvoker();
        GroupMessageAppService appService = new GroupMessageAppService(
                null,
                groupChatRepository,
                groupMemberRepository,
                null,
                null,
                null,
                null,
                notifierInvoker,
                null);
        ImGroupMessageSentEvent event = new ImGroupMessageSentEvent();
        event.setGroupId(1001L);
        event.setSenderId(1L);
        event.setMessageId(900L);
        event.setMessageType(com.co.kc.imchat.model.enums.ImMessageTypeEnum.TEXT);
        event.setMessageContent("hello");
        event.setSendTime(java.time.LocalDateTime.now());

        appService.onMessageSent(event);

        assertThat(notifierInvoker.groupSentCommands)
                .extracting(ImGroupSentNotifyCmd::getReceiverId)
                .containsExactly(2L);
        assertThat(notifierInvoker.groupSentCommands)
                .extracting(ImGroupSentNotifyCmd::getChatId)
                .containsExactly(102L);
    }

    @Test
    void openGroupChatMarksUnreadInboxMessagesRead() {
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L, 902L, 2));
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        RecordingGroupInboxRepository inboxRepository = new RecordingGroupInboxRepository();
        inboxRepository.unreadMessages.add(groupInboxMessage(901L, 101L, 1001L, 1L, 2L));
        inboxRepository.unreadMessages.add(groupInboxMessage(902L, 101L, 1001L, 1L, 2L));
        ChatAppService appService = new ChatAppService(
                null,
                null,
                groupChatRepository,
                groupMemberRepository,
                inboxRepository,
                null,
                new ImChatService(null, null, null, null, null, new SignedInSessionRepository()));
        ImGroupChatOpenCmd command = new ImGroupChatOpenCmd(1L, 101L);

        ImChatOpenDTO result = appService.openGroupChat(command);

        assertThat(result.getChatId()).isEqualTo(101L);
        assertThat(groupChatRepository.findSavedByUserId(1L).getUnreadMessageCount()).isZero();
        assertThat(groupChatRepository.findSavedByUserId(1L).getReadMessageId().getValue()).isEqualTo(902L);
        assertThat(inboxRepository.savedMessages).hasSize(2);
        assertThat(inboxRepository.savedMessages)
                .allSatisfy(message -> {
                    assertThat(message.getStatus()).isEqualTo(ImGroupMessageStatus.READ);
                    assertThat(message.getReadTime()).isNotNull();
                });
    }

    @Test
    void revokeGroupMessageRevokesSenderInboxOnlyOnce() {
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        RecordingGroupInboxRepository inboxRepository = new RecordingGroupInboxRepository();
        ImGroupInboxMessage senderMessage = groupInboxMessage(900L, 101L, 1001L, 1L, 1L);
        ImGroupInboxMessage receiverMessage = groupInboxMessage(900L, 102L, 1001L, 2L, 1L);
        inboxRepository.messages.add(senderMessage);
        inboxRepository.messages.add(receiverMessage);
        GroupMessageAppService appService = new GroupMessageAppService(
                new FixedSnowflakeId(900L),
                groupChatRepository,
                groupMemberRepository,
                inboxRepository,
                new NonChattingUserService(),
                new ImGroupService(groupMemberRepository, groupChatRepository),
                new ImMessageService(new FixedSnowflakeId(1L)),
                null,
                new NoopDomainEventPublisher());
        ImGroupMessageRevokeCmd command = new ImGroupMessageRevokeCmd();
        command.setChatId(101L);
        command.setUserId(1L);
        command.setMessageId(900L);

        appService.revokeMessage(command);

        assertThat(inboxRepository.savedMessages).hasSize(2);
        assertThat(inboxRepository.savedMessages)
                .allSatisfy(message -> assertThat(message.getStatus()).isEqualTo(ImGroupMessageStatus.REVOKED));
    }

    @Test
    void queryGroupMessageDetailThrowsWhenMessageMissing() {
        RecordingGroupChatRepository groupChatRepository = new RecordingGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        RecordingGroupMemberRepository groupMemberRepository = new RecordingGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        GroupMessageAppService appService = new GroupMessageAppService(
                new FixedSnowflakeId(900L),
                groupChatRepository,
                groupMemberRepository,
                new RecordingGroupInboxRepository(),
                new NonChattingUserService(),
                new ImGroupService(groupMemberRepository, groupChatRepository),
                new ImMessageService(new FixedSnowflakeId(1L)),
                null,
                new NoopDomainEventPublisher());
        ImGroupMessageDetailQuery query = new ImGroupMessageDetailQuery(101L, 1L, "missing");

        assertThatThrownBy(() -> appService.queryMessageDetail(query))
                .isInstanceOf(NotFoundException.class);
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
                .lastMessageId(lastMessageId == null ? null : new ImMessageId(lastMessageId))
                .unreadMessageCount(unreadMessageCount)
                .build();
    }

    private ImGroupInboxMessage groupInboxMessage(Long messageId, Long chatId, Long groupId, Long userId, Long senderId) {
        return ImGroupInboxMessage.builder()
                .id(new ImMessageId(messageId))
                .token(new ImMessageToken("token-" + messageId))
                .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                .chatId(new ImChatId(chatId))
                .groupId(new ImGroupId(groupId))
                .userId(new UserId(userId))
                .senderId(new UserId(senderId))
                .status(ImGroupMessageStatus.RECEIVED)
                .sendTime(java.time.LocalDateTime.now())
                .receivedTime(java.time.LocalDateTime.now())
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

    private static class NonChattingUserService extends UserService {
        NonChattingUserService() {
            super(null, null, new EmptySessionRepository(), (PasswordService) null);
        }

        @Override
        public boolean isChatting(ImChatId chatId, UserId receiverId) {
            return false;
        }
    }

    private static class EmptySessionRepository implements SessionRepository {
        @Override
        public com.co.kc.imchat.domain.session.Session find(UserId userId) {
            return null;
        }

        @Override
        public void save(com.co.kc.imchat.domain.session.Session session) {
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

    private static class NoopDomainEventPublisher implements DomainEventPublisher {
        @Override
        public void publish(com.co.kc.imchat.domain.shared.DomainEvent event) {
        }

        @Override
        public void publish(List<com.co.kc.imchat.domain.shared.DomainEvent> eventList) {
        }
    }

    private static class RecordingNotifierInvoker extends ImMessageNotifierInvoker {
        private final List<ImGroupSentNotifyCmd> groupSentCommands = new ArrayList<>();

        RecordingNotifierInvoker() {
            super(null, null);
        }

        @Override
        public <T> void invoke(T command) {
            if (command instanceof ImGroupSentNotifyCmd) {
                groupSentCommands.add((ImGroupSentNotifyCmd) command);
            }
        }
    }

    private static class RecordingGroupChatRepository implements ImGroupChatRepository {
        private final List<ImGroupChat> groupChats = new ArrayList<>();
        private List<ImGroupChat> savedGroupChats = new ArrayList<>();

        @Override
        public ImGroupChat find(ImChatId chatId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getId().getValue().equals(chatId.getValue()))
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
                    .filter(groupChat -> groupChat.getGroupId().getValue().equals(groupId.getValue()))
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
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupChat> findByUserIdAndChatIds(UserId userId, List<ImChatId> chatIds) {
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupChat> findByUserIdsAndGroupId(ImGroupId groupId, List<UserId> userIds) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getGroupId().equals(groupId))
                    .filter(groupChat -> userIds.contains(groupChat.getUserId()))
                    .collect(Collectors.toList());
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

        private ImGroupChat findSavedByUserId(Long userId) {
            return savedGroupChats.stream()
                    .filter(groupChat -> groupChat.getUserId().getValue().equals(userId))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
        }
    }

    private static class RecordingGroupMemberRepository implements ImGroupMemberRepository {
        private final List<ImGroupMember> members = new ArrayList<>();
        private List<ImGroupMember> savedMembers = new ArrayList<>();

        @Override
        public List<ImGroupMember> find(ImGroupId groupId) {
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
        public Map<ImGroupId, Integer> countByGroupIds(List<ImGroupId> groupIds) {
            return members.stream()
                    .filter(member -> groupIds.contains(member.getGroupId()))
                    .collect(Collectors.groupingBy(ImGroupMember::getGroupId, Collectors.summingInt(member -> 1)));
        }

        @Override
        public void saveAll(List<ImGroupMember> members) {
            savedMembers = new ArrayList<>(members);
        }
    }

    private static class RecordingGroupInboxRepository implements ImGroupInboxMessageRepository {
        private final List<ImGroupInboxMessage> messages = new ArrayList<>();
        private List<ImGroupInboxMessage> savedMessages = new ArrayList<>();
        private final List<ImGroupInboxMessage> unreadMessages = new ArrayList<>();

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
            return messages.stream()
                    .filter(message -> message.getChatId().equals(chatId))
                    .filter(message -> message.getUserId().equals(userId))
                    .filter(message -> message.getId().getValue().equals(messageId.getValue()))
                    .findFirst();
        }

        @Override
        public List<ImGroupInboxMessage> findByGroupIdAndMessageId(ImGroupId groupId, ImMessageId messageId) {
            return messages.stream()
                    .filter(message -> message.getGroupId().equals(groupId))
                    .filter(message -> message.getId().getValue().equals(messageId.getValue()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ImGroupInboxMessage> findUnreadMessages(ImChatId chatId, UserId userId) {
            return unreadMessages.stream()
                    .filter(message -> message.getChatId().getValue().equals(chatId.getValue()))
                    .filter(message -> message.getUserId().getValue().equals(userId.getValue()))
                    .collect(Collectors.toList());
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

        private ImGroupInboxMessage findSavedByUserId(Long userId) {
            return savedMessages.stream()
                    .filter(message -> message.getUserId().getValue().equals(userId))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
        }
    }
}
