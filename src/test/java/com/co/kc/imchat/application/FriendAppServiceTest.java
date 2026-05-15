package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatStatus;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.friend.FriendService;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserEmail;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserName;
import com.co.kc.imchat.domain.user.UserRepository;
import com.co.kc.imchat.model.cqrs.command.friend.FriendAddCmd;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FriendAppServiceTest {

    @Test
    void addFriendCreatesHiddenPrivateChatsForBothSides() {
        MemoryUserRepository userRepository = new MemoryUserRepository();
        userRepository.users.add(user(1L, "alice"));
        userRepository.users.add(user(2L, "bob"));
        MemoryFriendRepository friendRepository = new MemoryFriendRepository();
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        FriendAppService appService = new FriendAppService(
                userRepository,
                friendRepository,
                privateChatRepository,
                new FriendService(),
                new ImChatService(new FixedSnowflakeId(3000L), null, null, null, null, null));

        appService.addFriend(new FriendAddCmd(1L, 2L));

        assertThat(friendRepository.savedFriends)
                .extracting(friend -> friend.getUserId().getValue())
                .containsExactly(1L, 2L);
        assertThat(privateChatRepository.savedChats)
                .extracting(chat -> chat.getId().getValue())
                .containsExactly(3000L, 3001L);
        assertThat(privateChatRepository.savedChats)
                .extracting(chat -> chat.getUserId().getValue())
                .containsExactly(1L, 2L);
        assertThat(privateChatRepository.savedChats)
                .extracting(chat -> chat.getPeerUserId().getValue())
                .containsExactly(2L, 1L);
        assertThat(privateChatRepository.savedChats)
                .extracting(ImPrivateChat::getType)
                .containsExactly(ImChatType.PRIVATE, ImChatType.PRIVATE);
        assertThat(privateChatRepository.savedChats)
                .extracting(ImPrivateChat::getStatus)
                .containsExactly(ImChatStatus.HIDDEN, ImChatStatus.HIDDEN);
    }

    private User user(Long userId, String username) {
        return new User(new UserId(userId), new UserEmail(username + "@example.com"), new UserName(username), null);
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
            return users.stream()
                    .filter(user -> user.getEmail().equals(email))
                    .findFirst();
        }

        @Override
        public void save(User user) {
            users.add(user);
        }

        @Override
        public void remove(User user) {
            users.remove(user);
        }

        @Override
        public boolean contain(UserEmail email) {
            return find(email).isPresent();
        }
    }

    private static class MemoryFriendRepository implements FriendRepository {
        private final List<Friend> savedFriends = new ArrayList<>();

        @Override
        public List<Friend> find(UserId userId) {
            return savedFriends.stream()
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
        public void save(Friend friend) {
            savedFriends.add(friend);
        }

        @Override
        public void remove(UserId userId, UserId friendUserId) {
            savedFriends.removeIf(friend -> friend.getUserId().equals(userId)
                    && friend.getFriendUserId().equals(friendUserId));
        }
    }

    private static class MemoryPrivateChatRepository implements ImPrivateChatRepository {
        private final List<ImPrivateChat> savedChats = new ArrayList<>();

        @Override
        public List<ImPrivateChat> find(UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public Optional<ImPrivateChat> find(com.co.kc.imchat.domain.chat.ImChatId chatId) {
            return Optional.empty();
        }

        @Override
        public Optional<ImPrivateChat> find(UserId userId, UserId peerUserId) {
            return Optional.empty();
        }

        @Override
        public List<ImMessage> findLastMessageList(List<com.co.kc.imchat.domain.chat.ImChatId> chatIds, UserId viewer) {
            return Collections.emptyList();
        }

        @Override
        public void save(ImPrivateChat imPrivateChat) {
            savedChats.add(imPrivateChat);
        }
    }

    private static class FixedSnowflakeId extends SnowflakeId {
        private long next;

        FixedSnowflakeId(long next) {
            super(new com.co.kc.imchat.support.identity.snowflake.impl.StaticSnowflakeMachineId(1, 1));
            this.next = next;
        }

        @Override
        public synchronized Long next() {
            return next++;
        }
    }
}
