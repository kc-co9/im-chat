package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupService;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.command.chat.ImPrivateChatOpenCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatOpenDTO;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
                null,
                new NormalFriendRepository(),
                new ImGroupService(null, null),
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
