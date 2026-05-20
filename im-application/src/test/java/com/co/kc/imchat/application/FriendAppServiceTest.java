package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.model.ImChatStatus;
import com.co.kc.imchat.domain.chat.service.ImChatService;
import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.model.FriendAlias;
import com.co.kc.imchat.domain.friend.event.FriendAddedEvent;
import com.co.kc.imchat.domain.friend.event.FriendRemovedEvent;
import com.co.kc.imchat.domain.friend.model.FriendEdge;
import com.co.kc.imchat.domain.friend.model.FriendId;
import com.co.kc.imchat.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.domain.friend.service.FriendService;
import com.co.kc.imchat.domain.friend.model.FriendStatus;
import com.co.kc.imchat.domain.message.model.ImMessage;
import com.co.kc.imchat.domain.shared.event.DomainEvent;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.model.UserEmail;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
import com.co.kc.imchat.domain.user.repository.UserRepository;
import com.co.kc.imchat.common.identity.snowflake.impl.StaticSnowflakeMachineId;
import com.co.kc.imchat.application.support.event.DomainEventPublisher;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendAddCmd;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendAliasChangeCmd;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendBlockCmd;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendDeleteCmd;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendUnblockCmd;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.application.support.lock.DistributeLockScene;
import com.co.kc.imchat.application.support.lock.annotation.DistributeLock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FriendAppServiceTest {

    @Test
    void friendMutationMethodsShareStableUserPairLock() throws NoSuchMethodException {
        assertFriendPairLock(FriendAppService.class.getMethod("addFriend", FriendAddCmd.class));
        assertFriendPairLock(FriendAppService.class.getMethod("blockFriend", FriendBlockCmd.class));
        assertFriendPairLock(FriendAppService.class.getMethod("unblockFriend", FriendUnblockCmd.class));
        assertFriendPairLock(FriendAppService.class.getMethod("deleteFriend", FriendDeleteCmd.class));
        assertFriendPairLock(FriendAppService.class.getMethod("changeFriendAlias", FriendAliasChangeCmd.class));
    }

    @Test
    void addFriendCreatesHiddenPrivateChatsForBothSides() {
        MemoryUserRepository userRepository = new MemoryUserRepository();
        userRepository.users.add(user(1L, "alice"));
        userRepository.users.add(user(2L, "bob"));
        MemoryFriendRepository friendRepository = new MemoryFriendRepository();
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        FriendAppService appService = appService(userRepository, friendRepository, privateChatRepository);

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

    private void assertFriendPairLock(Method method) {
        DistributeLock lock = method.getAnnotation(DistributeLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.scene()).isEqualTo(DistributeLockScene.FRIEND_ADD);
        assertThat(lock.key()).isEqualTo(
                "#LockKeys.userPair(#command.userId, #command.friendUserId)");
    }

    @Test
    void changeFriendAliasUpdatesOnlyCurrentUsersFriendRow() {
        MemoryUserRepository userRepository = new MemoryUserRepository();
        userRepository.users.add(user(1L, "alice"));
        userRepository.users.add(user(2L, "bob"));
        MemoryFriendRepository friendRepository = new MemoryFriendRepository();
        friendRepository.savedFriends.add(friend(1L, 2L, "bob"));
        friendRepository.savedFriends.add(friend(2L, 1L, "alice"));
        FriendAppService appService = appService(userRepository, friendRepository, new MemoryPrivateChatRepository());

        appService.changeFriendAlias(new FriendAliasChangeCmd(1L, 2L, "bobby"));

        assertThat(friendRepository.find(new FriendEdge(new UserId(1L), new UserId(2L))).get().getFriendAlias())
                .extracting(FriendAlias::getValue)
                .isEqualTo("bobby");
        assertThat(friendRepository.find(new FriendEdge(new UserId(2L), new UserId(1L))).get().getFriendAlias()).isNull();
    }

    @Test
    void blockAndUnblockFriendUpdatesCurrentUsersFriendStatus() {
        MemoryFriendRepository friendRepository = new MemoryFriendRepository();
        friendRepository.savedFriends.add(friend(1L, 2L, "bob"));
        FriendAppService appService = appService(new MemoryUserRepository(), friendRepository, new MemoryPrivateChatRepository());

        appService.blockFriend(new FriendBlockCmd(1L, 2L));
        assertThat(friendRepository.find(new FriendEdge(new UserId(1L), new UserId(2L))).get().getStatus())
                .isEqualTo(FriendStatus.BLOCKED);

        appService.unblockFriend(new FriendUnblockCmd(1L, 2L));
        assertThat(friendRepository.find(new FriendEdge(new UserId(1L), new UserId(2L))).get().getStatus())
                .isEqualTo(FriendStatus.NORMAL);
    }

    @Test
    void deleteFriendPublishesEventToRemoveCurrentUsersPrivateChat() {
        MemoryFriendRepository friendRepository = new MemoryFriendRepository();
        friendRepository.savedFriends.add(friend(1L, 2L, "bob"));
        friendRepository.savedFriends.add(friend(2L, 1L, "alice"));
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        SyncFriendEventPublisher eventPublisher = new SyncFriendEventPublisher();
        FriendAppService appService = appService(
                new MemoryUserRepository(), friendRepository, privateChatRepository, eventPublisher);

        appService.deleteFriend(new FriendDeleteCmd(1L, 2L));

        assertThat(friendRepository.deletedPairs).containsExactly("1:2");
        assertThat(privateChatRepository.deletedPairs).containsExactly("1:2");
        assertThat(eventPublisher.events)
                .hasOnlyElementsOfType(FriendRemovedEvent.class);
        assertThat(friendRepository.findCalls).contains("1:2");
        assertThat(friendRepository.savedFriends)
                .extracting(friend -> friend.getUserId().getValue())
                .containsExactly(1L, 2L);
        assertThat(friendRepository.savedFriends)
                .extracting(Friend::getStatus)
                .containsExactly(FriendStatus.NORMAL, FriendStatus.NORMAL);
    }

    @Test
    void addFriendAfterLogicalDeleteCreatesNewFriendRowsAndHiddenPrivateChats() {
        MemoryUserRepository userRepository = new MemoryUserRepository();
        userRepository.users.add(user(1L, "alice"));
        userRepository.users.add(user(2L, "bob"));
        MemoryFriendRepository friendRepository = new MemoryFriendRepository();
        friendRepository.deletedPairs.add("1:2");
        friendRepository.deletedPairs.add("2:1");
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        FriendAppService appService = appService(userRepository, friendRepository, privateChatRepository);

        appService.addFriend(new FriendAddCmd(1L, 2L));

        assertThat(friendRepository.savedFriends)
                .extracting(friend -> friend.getUserId().getValue())
                .containsExactly(1L, 2L);
        assertThat(privateChatRepository.savedChats)
                .extracting(ImPrivateChat::getStatus)
                .containsExactly(ImChatStatus.HIDDEN, ImChatStatus.HIDDEN);
    }

    @Test
    void addFriendOnlyCreatesMissingSideWhenPeerSideAlreadyActive() {
        MemoryUserRepository userRepository = new MemoryUserRepository();
        userRepository.users.add(user(1L, "alice"));
        userRepository.users.add(user(2L, "bob"));
        MemoryFriendRepository friendRepository = new MemoryFriendRepository();
        friendRepository.savedFriends.add(friend(2L, 1L, "alice"));
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.existingChats.add(privateChat(2000L, 2L, 1L));
        FriendAppService appService = appService(userRepository, friendRepository, privateChatRepository);

        appService.addFriend(new FriendAddCmd(1L, 2L));

        assertThat(friendRepository.savedFriendWrites)
                .extracting(friend -> friend.getUserId().getValue())
                .containsExactly(1L);
        assertThat(privateChatRepository.savedChats)
                .extracting(chat -> chat.getUserId().getValue())
                .containsExactly(1L);
        assertThat(privateChatRepository.savedChats)
                .extracting(chat -> chat.getPeerUserId().getValue())
                .containsExactly(2L);
    }

    @Test
    void prepareHiddenPrivateChatReturnsEmptyWhenChatAlreadyExists() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.existingChats.add(privateChat(2000L, 1L, 2L));
        ImChatService service =
                new ImChatService(null, privateChatRepository, null, null, null, new FixedSnowflakeId(3000L));

        Optional<ImPrivateChat> result =
                service.prepareHiddenPrivateChat(new UserId(1L), new UserId(2L));

        assertThat(result).isEmpty();
    }

    @Test
    void prepareHiddenPrivateChatCreatesHiddenChatWhenMissing() {
        ImChatService service = new ImChatService(
                null, new MemoryPrivateChatRepository(), null, null, null, new FixedSnowflakeId(3000L));

        Optional<ImPrivateChat> result =
                service.prepareHiddenPrivateChat(new UserId(1L), new UserId(2L));

        assertThat(result).isPresent();
        assertThat(result.get().getId().getValue()).isEqualTo(3000L);
        assertThat(result.get().getStatus()).isEqualTo(ImChatStatus.HIDDEN);
    }

    private FriendAppService appService(MemoryUserRepository userRepository,
                                        MemoryFriendRepository friendRepository,
                                        MemoryPrivateChatRepository privateChatRepository) {
        SyncFriendEventPublisher eventPublisher = new SyncFriendEventPublisher();
        return appService(userRepository, friendRepository, privateChatRepository, eventPublisher);
    }

    private FriendAppService appService(MemoryUserRepository userRepository,
                                        MemoryFriendRepository friendRepository,
                                        MemoryPrivateChatRepository privateChatRepository,
                                        SyncFriendEventPublisher eventPublisher) {
        FriendAppService appService = new FriendAppService(
                userRepository,
                friendRepository,
                privateChatRepository,
                new FriendService(friendRepository),
                new ImChatService(null, privateChatRepository, null, null, null, new FixedSnowflakeId(3000L)),
                eventPublisher);
        eventPublisher.friendAppService = appService;
        return appService;
    }

    private User user(Long userId, String username) {
        return new User(new UserId(userId), new UserEmail(username + "@example.com"), new UserName(username), null);
    }

    private Friend friend(Long userId, Long friendUserId, String friendName) {
        return new Friend(new FriendId(new UserId(userId), new UserId(friendUserId)),
                new UserId(userId),
                new UserId(friendUserId),
                new UserName(friendName),
                null,
                FriendStatus.NORMAL,
                null);
    }

    private ImPrivateChat privateChat(Long chatId, Long userId, Long peerUserId) {
        return ImPrivateChat.builder()
                .id(new ImChatId(chatId))
                .type(ImChatType.PRIVATE)
                .userId(new UserId(userId))
                .peerUserId(new UserId(peerUserId))
                .build();
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
        private final List<Friend> savedFriendWrites = new ArrayList<>();
        private final List<String> deletedPairs = new ArrayList<>();
        private final List<String> findCalls = new ArrayList<>();
        private final List<String> containCalls = new ArrayList<>();

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
        public Optional<Friend> find(FriendEdge edge) {
            findCalls.add(edge.getUserId().getValue() + ":" + edge.getFriendUserId().getValue());
            return find(edge.getUserId()).stream()
                    .filter(friend -> friend.getFriendUserId().equals(edge.getFriendUserId()))
                    .findFirst();
        }

        @Override
        public boolean contain(UserId userId, UserId friendUserId) {
            containCalls.add(userId.getValue() + ":" + friendUserId.getValue());
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
            savedFriendWrites.add(friend);
            savedFriends.removeIf(savedFriend -> savedFriend.getUserId().equals(friend.getUserId())
                    && savedFriend.getFriendUserId().equals(friend.getFriendUserId()));
            savedFriends.add(friend);
        }

        @Override
        public void remove(Friend friend) {
            deletedPairs.add(friend.getUserId().getValue() + ":" + friend.getFriendUserId().getValue());
        }
    }

    private static class MemoryPrivateChatRepository implements ImPrivateChatRepository {
        private final List<ImPrivateChat> existingChats = new ArrayList<>();
        private final List<ImPrivateChat> savedChats = new ArrayList<>();
        private final List<String> deletedPairs = new ArrayList<>();

        @Override
        public List<ImPrivateChat> find(UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public Optional<ImPrivateChat> find(ImChatId chatId) {
            return Optional.empty();
        }

        @Override
        public Optional<ImPrivateChat> find(UserId userId, UserId peerUserId) {
            return existingChats.stream()
                    .filter(chat -> chat.getUserId().equals(userId))
                    .filter(chat -> chat.getPeerUserId().equals(peerUserId))
                    .findFirst();
        }

        @Override
        public boolean contain(UserId userId, UserId peerUserId) {
            return existingChats.stream()
                    .anyMatch(chat -> chat.getUserId().equals(userId)
                            && chat.getPeerUserId().equals(peerUserId));
        }

        @Override
        public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer) {
            return Collections.emptyList();
        }

        @Override
        public void save(ImPrivateChat imPrivateChat) {
            savedChats.add(imPrivateChat);
        }

        @Override
        public void remove(UserId userId, UserId peerUserId) {
            deletedPairs.add(userId.getValue() + ":" + peerUserId.getValue());
        }
    }

    private static class SyncFriendEventPublisher implements DomainEventPublisher {
        private FriendAppService friendAppService;
        private final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            events.add(event);
            if (event instanceof FriendAddedEvent) {
                friendAppService.onFriendAdded((FriendAddedEvent) event);
            }
            if (event instanceof FriendRemovedEvent) {
                friendAppService.onFriendRemoved((FriendRemovedEvent) event);
            }
        }

        @Override
        public void publish(List<DomainEvent> eventList) {
            eventList.forEach(this::publish);
        }
    }

    private static class FixedSnowflakeId extends SnowflakeId {
        private long next;

        FixedSnowflakeId(long next) {
            super(new StaticSnowflakeMachineId(1, 1));
            this.next = next;
        }

        @Override
        public synchronized Long next() {
            return next++;
        }
    }
}
