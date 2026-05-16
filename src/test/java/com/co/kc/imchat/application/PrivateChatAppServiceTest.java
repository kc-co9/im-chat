package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatStatus;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.message.ImMessageContent;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImPrivateMessageStatus;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserService;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.command.chat.ImPrivateChatOpenCmd;
import com.co.kc.imchat.model.cqrs.command.chat.PrivateChatHideCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateChatOpenDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.co.kc.imchat.support.event.DomainEventPublisher;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.support.lock.DistributeLockScene;
import com.co.kc.imchat.support.lock.annotation.DistributeLock;
import com.co.kc.imchat.support.notifier.ImMessageNotifierInvoker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivateChatAppServiceTest {

    @Test
    void openPrivateChatUsesStableUserPairLockKey() throws NoSuchMethodException {
        Method method = ChatAppService.class.getMethod("openPrivateChat", ImPrivateChatOpenCmd.class);
        DistributeLock lock = method.getAnnotation(DistributeLock.class);

        assertThat(lock.scene()).isEqualTo(DistributeLockScene.PRIVATE_CHAT_OPEN);
        assertThat(lock.key()).isEqualTo(
                "T(com.co.kc.imchat.support.lock.LockKeys).userPair(#command.userId, #command.peerUserId)");
    }

    @Test
    void openPrivateChatActivatesExistingCurrentUserChatOnly() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        ImPrivateChat userChat = privateChat(101L, 1L, 2L);
        userChat.hide();
        ImPrivateChat peerChat = privateChat(102L, 2L, 1L);
        peerChat.hide();
        privateChatRepository.chats.add(userChat);
        privateChatRepository.chats.add(peerChat);
        SignedInSessionRepository sessionRepository = new SignedInSessionRepository();
        ChatAppService appService = new ChatAppService(
                privateChatRepository,
                null,
                null,
                null,
                null,
                new NormalFriendRepository(),
                new ImChatService(null, null, privateChatRepository, null, null, sessionRepository));
        ImPrivateChatOpenCmd command = new ImPrivateChatOpenCmd(1L, 2L);

        ImPrivateChatOpenDTO result = appService.openPrivateChat(command);

        assertThat(result.getChatId()).isEqualTo(101L);
        assertThat(result.getPeerUserId()).isEqualTo(2L);

        assertThat(privateChatRepository.savedChats).containsExactly(userChat);
        assertThat(userChat.getStatus()).isEqualTo(ImChatStatus.NORMAL);
        assertThat(userChat.getActiveTime()).isNotNull();
        assertThat(peerChat.getStatus()).isEqualTo(ImChatStatus.HIDDEN);
        assertThat(sessionRepository.session.getChatId().getValue()).isEqualTo(101L);
    }

    @Test
    void openPrivateChatRejectsMissingChat() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        ChatAppService appService = new ChatAppService(
                privateChatRepository,
                null,
                null,
                null,
                null,
                new NormalFriendRepository(),
                new ImChatService(null, null, privateChatRepository, null, null, new SignedInSessionRepository()));

        assertThatThrownBy(() -> appService.openPrivateChat(new ImPrivateChatOpenCmd(1L, 2L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("聊天不存在");
    }

    @Test
    void openPrivateChatShowsHiddenChatAndUpdatesActiveTime() {
        LocalDateTime oldActiveTime = LocalDateTime.of(2026, 1, 1, 10, 0);
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        ImPrivateChat userChat = privateChat(101L, 1L, 2L);
        userChat.hide();
        userChat.setActiveTime(oldActiveTime);

        privateChatRepository.chats.add(userChat);
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));

        ChatAppService appService = new ChatAppService(
                privateChatRepository,
                null,
                null,
                null,
                null,
                new NormalFriendRepository(),
                new ImChatService(null, null, privateChatRepository, null, null, new SignedInSessionRepository()));

        ImPrivateChatOpenDTO result = appService.openPrivateChat(new ImPrivateChatOpenCmd(1L, 2L));

        assertThat(result.getChatId()).isEqualTo(101L);

        ImPrivateChat savedChat = privateChatRepository.savedChats.get(0);
        assertThat(savedChat.getStatus()).isEqualTo(ImChatStatus.NORMAL);
        assertThat(savedChat.getActiveTime()).isAfter(oldActiveTime);
    }

    @Test
    void hidePrivateChatHidesOnlyCurrentUserChat() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(101L, 1L, 2L));
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));

        ChatAppService appService = new ChatAppService(
                privateChatRepository,
                null,
                null,
                null,
                null,
                null,
                new ImChatService(null, null, privateChatRepository, null, null, null));

        appService.hidePrivateChat(new PrivateChatHideCmd(1L, 101L));

        assertThat(privateChatRepository.savedChats).hasSize(1);

        ImPrivateChat savedChat = privateChatRepository.savedChats.get(0);
        assertThat(savedChat.getStatus()).isEqualTo(ImChatStatus.HIDDEN);
    }

    @Test
    void revokePrivateMessageUsesOriginalSenderForBothInboxCopies() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(101L, 1L, 2L));
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));

        MemoryPrivateInboxRepository inboxRepository = new MemoryPrivateInboxRepository();
        inboxRepository.messages.add(privateMessage(900L, 101L, 1L, 1L, ImPrivateMessageStatus.SENT));
        inboxRepository.messages.add(privateMessage(900L, 102L, 2L, 1L, ImPrivateMessageStatus.RECEIVED));

        PrivateMessageAppService appService = new PrivateMessageAppService(
                new FixedSnowflakeId(900L),
                privateChatRepository,
                inboxRepository,
                null,
                new ImChatService(null, null, privateChatRepository, null, null, null),
                new ImMessageService(new FixedSnowflakeId(1L)),
                null,
                new MemoryDomainEventPublisher());

        ImPrivateMessageRevokeCmd command = new ImPrivateMessageRevokeCmd();
        command.setChatId(101L);
        command.setUserId(1L);
        command.setMessageId(900L);

        appService.revokeMessage(command);

        assertThat(inboxRepository.savedMessages)
                .extracting(ImPrivateInboxMessage::getStatus)
                .containsExactly(ImPrivateMessageStatus.REVOKED, ImPrivateMessageStatus.REVOKED);
    }

    @Test
    void queryPrivateHistoryKeepsRevokedMessagesWithHiddenContent() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(101L, 1L, 2L));

        MemoryPrivateInboxRepository inboxRepository = new MemoryPrivateInboxRepository();
        inboxRepository.messages.add(privateMessage(902L, 101L, 1L, 2L, ImPrivateMessageStatus.SENT));
        inboxRepository.messages.add(privateMessage(
                901L, 101L, 1L, 2L, ImPrivateMessageStatus.REVOKED, LocalDateTime.now().minusMinutes(1)));
        inboxRepository.messages.add(privateMessage(
                900L, 101L, 1L, 2L, ImPrivateMessageStatus.REVOKED, LocalDateTime.now().minusMinutes(3)));

        PrivateMessageAppService appService = new PrivateMessageAppService(
                new FixedSnowflakeId(900L),
                privateChatRepository,
                inboxRepository,
                null,
                new ImChatService(null, null, privateChatRepository, null, null, null),
                new ImMessageService(new FixedSnowflakeId(1L)),
                null,
                new MemoryDomainEventPublisher());

        List<ImPrivateMessageDTO> messages = appService.queryHistoryMessage(
                new ImPrivateMessageHistoryQuery(101L, 1L, null, 20));

        assertThat(messages)
                .extracting(ImPrivateMessageDTO::getMessageId)
                .containsExactly(902L, 901L, 900L);
        assertThat(messages)
                .extracting(ImPrivateMessageDTO::getContent)
                .containsExactly("hello", null, null);
    }

    @Test
    void readPrivateMessageOnlyPersistsLocalReadState() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));

        MemoryPrivateInboxRepository inboxRepository = new MemoryPrivateInboxRepository();
        inboxRepository.messages.add(privateMessage(900L, 102L, 2L, 1L, ImPrivateMessageStatus.RECEIVED));

        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        PrivateMessageAppService appService = new PrivateMessageAppService(
                new FixedSnowflakeId(900L),
                privateChatRepository,
                inboxRepository,
                null,
                new ImChatService(null, null, privateChatRepository, null, null, null),
                new ImMessageService(new FixedSnowflakeId(1L)),
                null,
                eventPublisher);

        appService.readMessage(new ImPrivateMessageReadCmd(102L, 2L, 900L));

        ImPrivateInboxMessage savedMessage = inboxRepository.savedMessages.get(0);
        assertThat(savedMessage.getStatus()).isEqualTo(ImPrivateMessageStatus.READ);
        assertThat(savedMessage.getReadTime()).isNotNull();

        ImPrivateChat savedChat = privateChatRepository.savedChats.get(0);
        assertThat(savedChat.getReadMessageId().getValue()).isEqualTo(900L);
        assertThat(savedChat.getUnreadMessageCount()).isZero();
        assertThat(eventPublisher.events).isEmpty();
    }

    @Test
    void sendPrivateMessageNotifiesOnlineReceiverAndKeepsUnreadWhenReceiverNotChatting() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(101L, 1L, 2L));
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));

        MemoryPrivateInboxRepository inboxRepository = new MemoryPrivateInboxRepository();
        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        RecordingNotifierInvoker notifierInvoker = new RecordingNotifierInvoker();
        PrivateMessageAppService appService = new PrivateMessageAppService(
                new FixedSnowflakeId(900L),
                privateChatRepository,
                inboxRepository,
                new OnlineNonChattingUserService(),
                new ImChatService(null, null, privateChatRepository, null, null, null),
                new ImMessageService(new FixedSnowflakeId(1L)),
                notifierInvoker,
                eventPublisher);

        appService.sendMessage(privateMessageSendCmd(101L, 1L));
        appService.onMessageSent((com.co.kc.imchat.domain.message.ImPrivateMessageSentEvent) eventPublisher.events.get(0));

        UserId receiverId = new UserId(2L);
        ImPrivateChat receiverChat = privateChatRepository.savedChats.stream()
                .filter(chat -> chat.belongsTo(receiverId))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(receiverChat.getUnreadMessageCount()).isEqualTo(1);
        assertThat(notifierInvoker.privateSentCommands)
                .extracting(ImPrivateSentNotifyCmd::getReceiverId)
                .containsExactly(2L);
    }

    private ImPrivateChat privateChat(Long chatId, Long userId, Long peerUserId) {
        return ImPrivateChat.builder()
                .id(new ImChatId(chatId))
                .type(ImChatType.PRIVATE)
                .userId(new UserId(userId))
                .peerUserId(new UserId(peerUserId))
                .build();
    }

    private ImPrivateInboxMessage privateMessage(
            Long messageId, Long chatId, Long userId, Long senderId, ImPrivateMessageStatus status) {
        return privateMessage(messageId, chatId, userId, senderId, status, null);
    }

    private ImPrivateInboxMessage privateMessage(
            Long messageId, Long chatId, Long userId, Long senderId,
            ImPrivateMessageStatus status, LocalDateTime revokeTime) {
        return ImPrivateInboxMessage.builder()
                .id(new ImMessageId(messageId))
                .token(new ImMessageToken("token-" + messageId))
                .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                .chatId(new ImChatId(chatId))
                .userId(new UserId(userId))
                .senderId(new UserId(senderId))
                .status(status)
                .sendTime(LocalDateTime.now())
                .revokeTime(revokeTime)
                .build();
    }

    private ImPrivateMessageSendCmd privateMessageSendCmd(Long chatId, Long userId) {
        ImPrivateMessageSendCmd command = new ImPrivateMessageSendCmd();
        command.setChatId(chatId);
        command.setUserId(userId);
        command.setMessageToken("token-1");
        command.setMessageType(ImMessageType.TEXT);
        command.setMessageContent("hello");
        return command;
    }

    private static class MemoryPrivateChatRepository implements ImPrivateChatRepository {
        private final List<ImPrivateChat> chats = new ArrayList<>();
        private final List<ImPrivateChat> savedChats = new ArrayList<>();

        @Override
        public List<ImPrivateChat> find(UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public Optional<ImPrivateChat> find(ImChatId chatId) {
            return chats.stream()
                    .filter(chat -> chat.getId().equals(chatId))
                    .findFirst();
        }

        @Override
        public Optional<ImPrivateChat> find(UserId userId, UserId peerUserId) {
            return chats.stream()
                    .filter(chat -> chat.getUserId().equals(userId))
                    .filter(chat -> chat.getPeerUserId().equals(peerUserId))
                    .findFirst();
        }

        @Override
        public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer) {
            return Collections.emptyList();
        }

        @Override
        public void save(ImPrivateChat imPrivateChat) {
            chats.add(imPrivateChat);
            savedChats.add(imPrivateChat);
        }
    }

    private static class MemoryPrivateInboxRepository implements ImPrivateInboxMessageRepository {
        private final List<ImPrivateInboxMessage> messages = new ArrayList<>();
        private final List<ImPrivateInboxMessage> savedMessages = new ArrayList<>();

        @Override
        public void save(ImPrivateInboxMessage message) {
            savedMessages.add(message);
        }

        @Override
        public void saveBatch(List<ImPrivateInboxMessage> messages) {
            savedMessages.addAll(messages);
        }

        @Override
        public Optional<ImPrivateInboxMessage> find(ImChatId chatId, ImMessageId messageId) {
            return messages.stream()
                    .filter(message -> message.getChatId().equals(chatId))
                    .filter(message -> message.getId().getValue().equals(messageId.getValue()))
                    .findFirst();
        }

        @Override
        public Optional<ImPrivateInboxMessage> find(ImChatId chatId, ImMessageToken messageToken) {
            return Optional.empty();
        }

        @Override
        public List<ImPrivateInboxMessage> queryHistory(
                ImChatId imChatId, ImMessageId imLastMessageId, int count, UserId viewer) {
            return messages.stream()
                    .filter(message -> message.getChatId().equals(imChatId))
                    .filter(message -> message.getUserId().equals(viewer))
                    .filter(message -> imLastMessageId == null || message.getId().getValue() < imLastMessageId.getValue())
                    .sorted((left, right) -> right.getId().getValue().compareTo(left.getId().getValue()))
                    .limit(count)
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<ImPrivateInboxMessage> queryDetail(
                ImChatId chatId, ImMessageToken messageToken, UserId viewer) {
            return Optional.empty();
        }

        @Override
        public boolean contain(ImChatId chatId, ImMessageToken messageToken) {
            return false;
        }
    }

    private static class NormalFriendRepository implements FriendRepository {
        @Override
        public List<Friend> find(UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public List<Friend> find(UserId userId, List<UserId> friendUserIds) {
            return Collections.emptyList();
        }

        @Override
        public Optional<Friend> find(UserId userId, UserId friendUserId) {
            return Optional.of(new Friend());
        }

        @Override
        public boolean contain(UserId userId, UserId friendUserId) {
            return true;
        }

        @Override
        public void save(Friend friend) {
        }

        @Override
        public void remove(UserId userId, UserId friendUserId) {
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

    private static class MemoryDomainEventPublisher implements DomainEventPublisher {
        private final List<com.co.kc.imchat.domain.shared.DomainEvent> events = new ArrayList<>();

        @Override
        public void publish(com.co.kc.imchat.domain.shared.DomainEvent event) {
            events.add(event);
        }

        @Override
        public void publish(List<com.co.kc.imchat.domain.shared.DomainEvent> eventList) {
            events.addAll(eventList);
        }
    }

    private static class OnlineNonChattingUserService extends UserService {
        OnlineNonChattingUserService() {
            super(null, null, null, null);
        }

        @Override
        public boolean isChatting(ImChatId chatId, UserId receiverId) {
            return false;
        }

        @Override
        public boolean isOnline(UserId userId) {
            return true;
        }
    }

    private static class RecordingNotifierInvoker extends ImMessageNotifierInvoker {
        private final List<ImPrivateSentNotifyCmd> privateSentCommands = new ArrayList<>();

        RecordingNotifierInvoker() {
            super(null, null);
        }

        @Override
        public <T> void invoke(T command) {
            if (command instanceof ImPrivateSentNotifyCmd) {
                privateSentCommands.add((ImPrivateSentNotifyCmd) command);
            }
        }
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
}
