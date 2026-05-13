package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.message.ImMessageContent;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImPrivateMessageStatus;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.command.chat.ImPrivateChatOpenCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatOpenDTO;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.support.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateChatAppServiceTest {

    @Test
    void openPrivateChatCreatesBothSidesAndEntersCurrentUserChat() {
        RecordingPrivateChatRepository privateChatRepository = new RecordingPrivateChatRepository();
        SignedInSessionRepository sessionRepository = new SignedInSessionRepository();
        ChatAppService appService = new ChatAppService(
                new FixedSnowflakeId(3000L),
                privateChatRepository,
                null,
                null,
                null,
                new NormalFriendRepository(),
                new ImChatService(null, null, privateChatRepository, null, null, sessionRepository));
        ImPrivateChatOpenCmd command = new ImPrivateChatOpenCmd(1L, 2L);

        ImChatOpenDTO result = appService.openPrivateChat(command);

        assertThat(result.getChatId()).isEqualTo(3000L);
        assertThat(privateChatRepository.savedChats)
                .extracting(chat -> chat.getUserId().getValue())
                .containsExactly(1L, 2L);
        assertThat(privateChatRepository.savedChats)
                .extracting(chat -> chat.getPeerUserId().getValue())
                .containsExactly(2L, 1L);
        assertThat(privateChatRepository.savedChats)
                .extracting(chat -> chat.getId().getValue())
                .containsExactly(3000L, 3001L);
        assertThat(sessionRepository.session.getChatId().getValue()).isEqualTo(3000L);
    }

    @Test
    void revokePrivateMessageUsesOriginalSenderForBothInboxCopies() {
        RecordingPrivateChatRepository privateChatRepository = new RecordingPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(101L, 1L, 2L));
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));
        RecordingPrivateInboxRepository inboxRepository = new RecordingPrivateInboxRepository();
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
                new NoopDomainEventPublisher());
        ImPrivateMessageRevokeCmd command = new ImPrivateMessageRevokeCmd();
        command.setChatId(101L);
        command.setUserId(1L);
        command.setMessageId(900L);

        appService.revokeMessage(command);

        assertThat(inboxRepository.savedMessages)
                .extracting(ImPrivateInboxMessage::getStatus)
                .containsExactly(ImPrivateMessageStatus.REVOKED, ImPrivateMessageStatus.REVOKED);
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
        return ImPrivateInboxMessage.builder()
                .id(new ImMessageId(messageId))
                .token(new ImMessageToken("token-" + messageId))
                .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                .chatId(new ImChatId(chatId))
                .userId(new UserId(userId))
                .senderId(new UserId(senderId))
                .status(status)
                .sendTime(LocalDateTime.now())
                .build();
    }

    private static class RecordingPrivateChatRepository implements ImPrivateChatRepository {
        private final List<ImPrivateChat> chats = new ArrayList<>();
        private final List<ImPrivateChat> savedChats = new ArrayList<>();

        @Override
        public List<ImPrivateChat> find(UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public ImPrivateChat find(ImChatId chatId) {
            return chats.stream()
                    .filter(chat -> chat.getId().equals(chatId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public ImPrivateChat find(UserId userId, UserId peerUserId) {
            return chats.stream()
                    .filter(chat -> chat.getUserId().equals(userId))
                    .filter(chat -> chat.getPeerUserId().equals(peerUserId))
                    .findFirst()
                    .orElse(null);
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

    private static class RecordingPrivateInboxRepository implements ImPrivateInboxMessageRepository {
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
        public List<ImPrivateInboxMessage> queryHistory(ImChatId imChatId, ImMessageId imLastMessageId, int count, UserId viewer) {
            return Collections.emptyList();
        }

        @Override
        public Optional<ImPrivateInboxMessage> queryDetail(ImChatId chatId, ImMessageToken messageToken, UserId viewer) {
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
        public Friend find(UserId userId, UserId friendUserId) {
            return new Friend();
        }

        @Override
        public void save(Friend friend) {
        }

        @Override
        public void remove(Friend friend) {
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
