package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.chat.service.ImChatService;
import com.co.kc.imchat.domain.chat.model.ImChatStatus;
import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.model.FriendEdge;
import com.co.kc.imchat.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.domain.friend.service.FriendService;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.domain.group.model.GroupName;
import com.co.kc.imchat.domain.group.repository.GroupRepository;
import com.co.kc.imchat.domain.group.service.GroupService;
import com.co.kc.imchat.domain.group.model.GroupStatus;
import com.co.kc.imchat.domain.group.model.MemberCount;
import com.co.kc.imchat.domain.group.model.MemberId;
import com.co.kc.imchat.domain.message.event.ImPrivateMessageSentEvent;
import com.co.kc.imchat.domain.message.model.ImMessage;
import com.co.kc.imchat.domain.message.model.ImMessageContent;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.message.service.ImMessageService;
import com.co.kc.imchat.domain.message.model.ImMessageToken;
import com.co.kc.imchat.domain.message.model.ImMessageType;
import com.co.kc.imchat.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.message.repository.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.domain.message.model.ImPrivateMessageStatus;
import com.co.kc.imchat.domain.session.model.Session;
import com.co.kc.imchat.domain.session.repository.SessionRepository;
import com.co.kc.imchat.domain.shared.event.DomainEvent;
import com.co.kc.imchat.domain.user.service.UserService;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.common.identity.snowflake.ISnowflakeMachineId;
import com.co.kc.imchat.application.model.cqrs.command.chat.ImPrivateChatOpenCmd;
import com.co.kc.imchat.application.model.cqrs.command.chat.GroupAliasChangeCmd;
import com.co.kc.imchat.application.model.cqrs.command.chat.PrivateChatHideCmd;
import com.co.kc.imchat.application.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.co.kc.imchat.application.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.application.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.application.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.co.kc.imchat.application.model.cqrs.dto.im.ImPrivateChatOpenDTO;
import com.co.kc.imchat.application.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.application.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.co.kc.imchat.application.support.event.DomainEventPublisher;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.application.support.lock.DistributeLockScene;
import com.co.kc.imchat.application.support.lock.annotation.DistributeLock;
import com.co.kc.imchat.application.support.notifier.ImMessageNotifierInvoker;
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
                "#LockKeys.userPair(#command.userId(), #command.peerUserId())");
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
                new ImChatService(new NormalFriendRepository(), privateChatRepository, null, null, sessionRepository, null),
                new FriendService(new NormalFriendRepository()),
                null);
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
                new ImChatService(new NormalFriendRepository(), privateChatRepository, null, null, new SignedInSessionRepository(), null),
                new FriendService(new NormalFriendRepository()),
                null);

        assertThatThrownBy(() -> appService.openPrivateChat(new ImPrivateChatOpenCmd(1L, 2L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("聊天不存在");
    }

    @Test
    void openPrivateChatRequiresMutualNormalFriends() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(101L, 1L, 2L));
        ChatAppService appService = new ChatAppService(
                privateChatRepository,
                null,
                null,
                null,
                null,
                new ImChatService(new OneWayNormalFriendRepository(1L, 2L), privateChatRepository, null, null, new SignedInSessionRepository(), null),
                new FriendService(new OneWayNormalFriendRepository(1L, 2L)),
                null);

        assertThatThrownBy(() -> appService.openPrivateChat(new ImPrivateChatOpenCmd(1L, 2L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("好友不存在");
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
                new ImChatService(new NormalFriendRepository(), privateChatRepository, null, null, new SignedInSessionRepository(), null),
                new FriendService(new NormalFriendRepository()),
                null);

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
                new ImChatService(null, privateChatRepository, null, null, null, null),
                null,
                null);

        appService.hidePrivateChat(new PrivateChatHideCmd(1L, 101L));

        assertThat(privateChatRepository.savedChats).hasSize(1);

        ImPrivateChat savedChat = privateChatRepository.savedChats.get(0);
        assertThat(savedChat.getStatus()).isEqualTo(ImChatStatus.HIDDEN);
    }

    @Test
    void changeGroupAliasUpdatesOnlyCurrentUsersGroupChat() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        ImGroupChat userChat = ImGroupChat.builder()
                .id(new ImChatId(201L))
                .groupId(new GroupId(1001L))
                .userId(new UserId(1L))
                .type(ImChatType.GROUP)
                .unreadMessageCount(0)
                .build();
        groupChatRepository.groupChats.add(userChat);
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(1001L, 1L, "group"));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        ChatAppService appService = new ChatAppService(
                privateChatRepository,
                groupChatRepository,
                groupRepository,
                new GroupService(groupMemberRepository, groupChatRepository, groupRepository, null, null),
                null,
                new ImChatService(null, null, null, groupChatRepository, null, null),
                null,
                null);

        appService.changeGroupAlias(new GroupAliasChangeCmd(1L, 201L, "work"));

        assertThat(groupChatRepository.savedGroupChats).containsExactly(userChat);
        assertThat(userChat.getGroupAlias().getValue()).isEqualTo("work");
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
                privateChatRepository,
                inboxRepository,
                null,
                new ImChatService(null, privateChatRepository, null, null, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                null,
                new FixedSnowflakeId(900L),
                null,
                new MemoryDomainEventPublisher());

        ImPrivateMessageRevokeCmd command = new ImPrivateMessageRevokeCmd(1L, 101L, 900L);

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
                privateChatRepository,
                inboxRepository,
                null,
                new ImChatService(null, privateChatRepository, null, null, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                null,
                new FixedSnowflakeId(900L),
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
                privateChatRepository,
                inboxRepository,
                null,
                new ImChatService(null, privateChatRepository, null, null, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                null,
                new FixedSnowflakeId(900L),
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
                privateChatRepository,
                inboxRepository,
                new OnlineNonChattingUserService(),
                new ImChatService(new NormalFriendRepository(), privateChatRepository, null, null, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                new FriendService(new NormalFriendRepository()),
                new FixedSnowflakeId(900L),
                notifierInvoker,
                eventPublisher);

        appService.sendMessage(privateMessageSendCmd(101L, 1L));
        appService.onMessageSent((ImPrivateMessageSentEvent) eventPublisher.events.get(0));

        UserId receiverId = new UserId(2L);
        ImPrivateChat receiverChat = privateChatRepository.savedChats.stream()
                .filter(chat -> chat.belongsTo(receiverId))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(receiverChat.getUnreadMessageCount()).isEqualTo(1);
        assertThat(notifierInvoker.privateSentCommands)
                .extracting(ImPrivateSentNotifyCmd::receiverId)
                .containsExactly(2L);
    }

    @Test
    void sendPrivateMessageRequiresSenderNormalFriendRelation() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(101L, 1L, 2L));
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));
        MemoryPrivateInboxRepository inboxRepository = new MemoryPrivateInboxRepository();
        PrivateMessageAppService appService = new PrivateMessageAppService(
                privateChatRepository,
                inboxRepository,
                new OnlineNonChattingUserService(),
                new ImChatService(new MissingFriendRepository(), privateChatRepository, null, null, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                new FriendService(new MissingFriendRepository()),
                new FixedSnowflakeId(900L),
                new RecordingNotifierInvoker(),
                new MemoryDomainEventPublisher());

        assertThatThrownBy(() -> appService.sendMessage(privateMessageSendCmd(101L, 1L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("好友不存在");
        assertThat(inboxRepository.savedMessages).isEmpty();
    }

    @Test
    void sendPrivateMessageRequiresReceiverNormalFriendRelation() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(101L, 1L, 2L));
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));
        MemoryPrivateInboxRepository inboxRepository = new MemoryPrivateInboxRepository();
        PrivateMessageAppService appService = new PrivateMessageAppService(
                privateChatRepository,
                inboxRepository,
                new OnlineNonChattingUserService(),
                new ImChatService(new OneWayNormalFriendRepository(1L, 2L), privateChatRepository, null, null, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                new FriendService(new OneWayNormalFriendRepository(1L, 2L)),
                new FixedSnowflakeId(900L),
                new RecordingNotifierInvoker(),
                new MemoryDomainEventPublisher());

        assertThatThrownBy(() -> appService.sendMessage(privateMessageSendCmd(101L, 1L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("好友不存在");
        assertThat(inboxRepository.savedMessages).isEmpty();
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
        return new ImPrivateMessageSendCmd(userId, chatId, "token-1", ImMessageType.TEXT, "hello");
    }

    private Group group(Long groupId, Long ownerId, String name) {
        return Group.builder()
                .id(new GroupId(groupId))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(ownerId))
                .name(new GroupName(name))
                .memberCount(new MemberCount(1))
                .status(GroupStatus.ACTIVE)
                .build();
    }

    private GroupMember groupMember(Long groupId, Long userId) {
        return GroupMember.builder()
                .id(new MemberId(new GroupId(groupId), new UserId(userId)))
                .groupId(new GroupId(groupId))
                .userId(new UserId(userId))
                .joinTime(LocalDateTime.now())
                .build();
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
        public boolean contain(UserId userId, UserId peerUserId) {
            return chats.stream()
                    .anyMatch(chat -> chat.getUserId().equals(userId)
                            && chat.getPeerUserId().equals(peerUserId));
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

        @Override
        public void remove(UserId userId, UserId peerUserId) {
            chats.removeIf(chat -> chat.getUserId().equals(userId)
                    && chat.getPeerUserId().equals(peerUserId));
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

    private static class MemoryGroupChatRepository implements ImGroupChatRepository {
        private final List<ImGroupChat> groupChats = new ArrayList<>();
        private final List<ImGroupChat> savedGroupChats = new ArrayList<>();

        @Override
        public Optional<ImGroupChat> find(ImChatId chatId) {
            return groupChats.stream()
                    .filter(chat -> chat.getId().equals(chatId))
                    .findFirst();
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
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupChat> find(UserId userId) {
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupChat> find(UserId userId, java.util.Collection<GroupId> groupIds) {
            return Collections.emptyList();
        }

        @Override
        public List<ImGroupChat> find(
                GroupId groupId, List<UserId> memberIds) {
            return Collections.emptyList();
        }

        @Override
        public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer) {
            return Collections.emptyList();
        }

        @Override
        public void save(ImGroupChat groupChat) {
            savedGroupChats.add(groupChat);
        }

        @Override
        public void save(List<ImGroupChat> groupChats) {
            savedGroupChats.addAll(groupChats);
        }

        @Override
        public boolean contain(ImChatId chatId, UserId userId) {
            return find(chatId).map(chat -> chat.belongsTo(userId)).orElse(false);
        }

        @Override
        public void remove(GroupId groupId, UserId userId) {
            groupChats.removeIf(chat -> chat.getGroupId().equals(groupId)
                    && chat.getUserId().equals(userId));
        }
    }

    private static class MemoryGroupRepository implements GroupRepository {
        private final List<Group> groups = new ArrayList<>();

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
                    .collect(Collectors.toList());
        }

        @Override
        public void save(Group group) {
        }
    }

    private static class MemoryGroupMemberRepository implements GroupMemberRepository {
        private final List<GroupMember> members = new ArrayList<>();

        @Override
        public List<GroupMember> find(GroupId groupId) {
            return members.stream()
                    .filter(member -> member.getGroupId().equals(groupId))
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<GroupMember> find(GroupId groupId, UserId userId) {
            return members.stream()
                    .filter(member -> member.getGroupId().equals(groupId))
                    .filter(member -> member.getUserId().equals(userId))
                    .findFirst();
        }

        @Override
        public boolean contain(GroupId groupId, UserId userId) {
            return find(groupId, userId).isPresent();
        }

        @Override
        public void save(List<GroupMember> members) {
        }

        @Override
        public void save(GroupMember member) {
        }

        @Override
        public void remove(GroupMember groupMember) {
            members.removeIf(member -> member.getGroupId().equals(groupMember.getGroupId())
                    && member.getUserId().equals(groupMember.getUserId()));
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
        public Optional<Friend> find(FriendEdge edge) {
            return Optional.of(new Friend());
        }

        @Override
        public boolean contain(UserId userId, UserId friendUserId) {
            return true;
        }

        @Override
        public boolean isFriendshipActive(UserId userId, UserId friendUserId) {
            return true;
        }

        @Override
        public void save(Friend friend) {
        }

        @Override
        public void remove(Friend friend) {
        }
    }

    private static class MissingFriendRepository extends NormalFriendRepository {
        @Override
        public boolean contain(UserId userId, UserId friendUserId) {
            return false;
        }

        @Override
        public boolean isFriendshipActive(UserId userId, UserId friendUserId) {
            return false;
        }
    }

    private static class OneWayNormalFriendRepository extends NormalFriendRepository {
        private final UserId userId;
        private final UserId friendUserId;

        private OneWayNormalFriendRepository(Long userId, Long friendUserId) {
            this.userId = new UserId(userId);
            this.friendUserId = new UserId(friendUserId);
        }

        @Override
        public boolean contain(UserId userId, UserId friendUserId) {
            return isFriendshipActive(userId, friendUserId);
        }

        @Override
        public boolean isFriendshipActive(UserId userId, UserId friendUserId) {
            return this.userId.equals(userId) && this.friendUserId.equals(friendUserId);
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
        private final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            events.add(event);
        }

        @Override
        public void publish(List<DomainEvent> eventList) {
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
}
