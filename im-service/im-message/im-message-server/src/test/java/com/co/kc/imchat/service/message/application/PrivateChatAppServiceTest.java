package com.co.kc.imchat.service.message.application;

import com.co.kc.imchat.service.message.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.service.ImChatService;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatStatus;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatType;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.service.message.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.message.event.ImPrivateMessageSentEvent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageContent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.service.ImMessageService;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.service.message.domain.message.repository.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateMessageStatus;
import com.co.kc.imchat.common.domain.shared.event.DomainEvent;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.identity.snowflake.ISnowflakeMachineId;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.ImPrivateChatOpenCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.GroupAliasChangeCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.PrivateChatHideCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageReceiveCmd;
import com.co.kc.imchat.service.message.application.notification.model.ImPrivateRevokedNotification;
import com.co.kc.imchat.service.message.application.notification.model.ImPrivateSentNotification;
import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImPrivateChatOpenDTO;
import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.service.message.model.cqrs.query.ImPrivateMessageDetailQuery;
import com.co.kc.imchat.service.message.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmableStore;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptTask;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.exception.RepeatException;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.service.message.adapter.account.AccountAdapter;
import com.co.kc.imchat.service.message.domain.social.model.FriendDisplay;
import com.co.kc.imchat.service.message.domain.social.model.GroupMessageRecipient;
import com.co.kc.imchat.service.message.domain.social.model.GroupSummary;
import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import com.co.kc.imchat.service.message.application.lock.ImMessageLockScene;
import com.co.kc.imchat.plugin.lock.annotation.DistributeLock;
import com.co.kc.imchat.service.message.application.notification.ImMessageNotifierInvoker;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivateChatAppServiceTest {

    @Test
    void openPrivateChatUsesStableUserPairLockKey() throws NoSuchMethodException {
        Method method = ChatAppService.class.getMethod("openPrivateChat", ImPrivateChatOpenCmd.class);
        DistributeLock lock = method.getAnnotation(DistributeLock.class);

        assertThat(lock.scene()).isEqualTo(ImMessageLockScene.PRIVATE_CHAT_OPEN);
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
        ChatAppService appService = new ChatAppService(
                privateChatRepository,
                null,
                null,
                new ImChatService(privateChatRepository, null, null),
                new TestSocialAdapter(new NormalFriendRepository(), null, null, null),
                null,
                null);
        ImPrivateChatOpenCmd command = new ImPrivateChatOpenCmd(1L, 2L);

        ImPrivateChatOpenDTO result = appService.openPrivateChat(command);

        assertThat(result.getChatId()).isEqualTo(101L);
        assertThat(result.getPeerUserId()).isEqualTo(2L);

        assertThat(privateChatRepository.savedChats).containsExactly(userChat);
        assertThat(userChat.getStatus()).isEqualTo(ImChatStatus.NORMAL);
        assertThat(userChat.getActiveTime()).isNotNull();
        assertThat(peerChat.getStatus()).isEqualTo(ImChatStatus.HIDDEN);
    }

    @Test
    void openPrivateChatRejectsMissingChat() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        ChatAppService appService = new ChatAppService(
                privateChatRepository,
                null,
                null,
                new ImChatService(privateChatRepository, null, null),
                new TestSocialAdapter(new NormalFriendRepository(), null, null, null),
                null,
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
                new ImChatService(privateChatRepository, null, null),
                new TestSocialAdapter(new OneWayNormalFriendRepository(1L, 2L), null, null, null),
                null,
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
                new ImChatService(privateChatRepository, null, null),
                new TestSocialAdapter(new NormalFriendRepository(), null, null, null),
                null,
                null);

        ImPrivateChatOpenDTO result = appService.openPrivateChat(new ImPrivateChatOpenCmd(1L, 2L));

        assertThat(result.getChatId()).isEqualTo(101L);

        ImPrivateChat savedChat = privateChatRepository.savedChats.getFirst();
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
                new ImChatService(privateChatRepository, null, null),
                new TestSocialAdapter(null, null, null, null),
                null,
                null);

        appService.hidePrivateChat(new PrivateChatHideCmd(1L, 101L));

        assertThat(privateChatRepository.savedChats).hasSize(1);

        ImPrivateChat savedChat = privateChatRepository.savedChats.getFirst();
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
                null,
                new ImChatService(null, groupChatRepository, null),
                new TestSocialAdapter(null, groupRepository, groupMemberRepository, groupChatRepository),
                null,
                null);

        appService.changeGroupAlias(new GroupAliasChangeCmd(1L, 201L, "work"));

        assertThat(groupChatRepository.savedGroupChats).containsExactly(userChat);
        assertThat(userChat.getGroupAlias().value()).isEqualTo("work");
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
                new ImChatService(privateChatRepository, null, null),
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
                new ImChatService(privateChatRepository, null, null),
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
                new ImChatService(privateChatRepository, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                null,
                new FixedSnowflakeId(900L),
                null,
                eventPublisher);

        appService.readMessage(new ImPrivateMessageReadCmd(102L, 2L, 900L));

        ImPrivateInboxMessage savedMessage = inboxRepository.savedMessages.getFirst();
        assertThat(savedMessage.getStatus()).isEqualTo(ImPrivateMessageStatus.READ);
        assertThat(savedMessage.getReadTime()).isNotNull();

        ImPrivateChat savedChat = privateChatRepository.savedChats.getFirst();
        assertThat(savedChat.getReadMessageId().value()).isEqualTo(900L);
        assertThat(savedChat.getUnreadMessageCount()).isZero();
        assertThat(eventPublisher.events).isEmpty();
    }

    @Test
    void receivePrivateMessageMarksOnlyReceiverInbox() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));

        MemoryPrivateInboxRepository inboxRepository = new MemoryPrivateInboxRepository();
        inboxRepository.messages.add(privateMessage(900L, 102L, 2L, 1L, ImPrivateMessageStatus.SENT));

        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        PrivateMessageAppService appService = new PrivateMessageAppService(
                privateChatRepository,
                inboxRepository,
                null,
                new ImChatService(privateChatRepository, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                null,
                new FixedSnowflakeId(900L),
                null,
                eventPublisher);

        appService.receiveMessage(new ImPrivateMessageReceiveCmd(2L, 102L, 900L));

        ImPrivateInboxMessage savedMessage = inboxRepository.savedMessages.getFirst();
        assertThat(savedMessage.getStatus()).isEqualTo(ImPrivateMessageStatus.RECEIVED);
        assertThat(savedMessage.getReceivedTime()).isNotNull();
        assertThat(privateChatRepository.savedChats).isEmpty();
        assertThat(eventPublisher.events).isEmpty();
    }

    @Test
    void sendPrivateMessageNotifiesReceiverAndKeepsUnreadWhenReceiverNotChatting() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(101L, 1L, 2L));
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));

        MemoryPrivateInboxRepository inboxRepository = new MemoryPrivateInboxRepository();
        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        RecordingNotifierInvoker notifierInvoker = new RecordingNotifierInvoker();
        PrivateMessageAppService appService = new PrivateMessageAppService(
                privateChatRepository,
                inboxRepository,
                new TestAccountAdapter(false),
                new ImChatService(privateChatRepository, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                new TestSocialAdapter(new NormalFriendRepository(), null, null, null),
                new FixedSnowflakeId(900L),
                notifierInvoker,
                eventPublisher);

        appService.sendMessage(privateMessageSendCmd(101L, 1L));
        appService.onMessageSent((ImPrivateMessageSentEvent) eventPublisher.events.getFirst());

        UserId receiverId = new UserId(2L);
        ImPrivateChat receiverChat = privateChatRepository.savedChats.stream()
                .filter(chat -> chat.belongsTo(receiverId))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(receiverChat.getUnreadMessageCount()).isEqualTo(1);
        assertThat(notifierInvoker.privateSentCommands)
                .extracting(ImPrivateSentNotification::receiverId)
                .containsExactly(2L);
    }

    @Test
    void sendPrivateMessageRejectsDuplicateTokenBeforeSavingMessages() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(101L, 1L, 2L));
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));

        MemoryPrivateInboxRepository inboxRepository = new MemoryPrivateInboxRepository();
        inboxRepository.messages.add(privateMessage(899L, 101L, 1L, 1L, ImPrivateMessageStatus.SENT, null, "token-1"));

        PrivateMessageAppService appService = new PrivateMessageAppService(
                privateChatRepository,
                inboxRepository,
                new TestAccountAdapter(false, 2L),
                new ImChatService(privateChatRepository, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                new TestSocialAdapter(new NormalFriendRepository(), null, null, null),
                new FixedSnowflakeId(900L),
                new RecordingNotifierInvoker(),
                new MemoryDomainEventPublisher());

        assertThatThrownBy(() -> appService.sendMessage(privateMessageSendCmd(101L, 1L)))
                .isInstanceOf(RepeatException.class)
                .hasMessageContaining("消息已存在");
        assertThat(inboxRepository.savedMessages).isEmpty();
        assertThat(privateChatRepository.savedChats).isEmpty();
    }

    @Test
    void sendPrivateMessageChecksReceiverChattingBeforeSavingMessages() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(101L, 1L, 2L));
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));
        MemoryPrivateInboxRepository inboxRepository = new MemoryPrivateInboxRepository();
        PrivateMessageAppService appService = new PrivateMessageAppService(
                privateChatRepository,
                inboxRepository,
                new ChattingFailureAccountAdapter(),
                new ImChatService(privateChatRepository, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                new TestSocialAdapter(new NormalFriendRepository(), null, null, null),
                new FixedSnowflakeId(900L),
                new RecordingNotifierInvoker(),
                new MemoryDomainEventPublisher());

        assertThatThrownBy(() -> appService.sendMessage(privateMessageSendCmd(101L, 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("session unavailable");
        assertThat(inboxRepository.savedMessages).isEmpty();
        assertThat(privateChatRepository.savedChats).isEmpty();
    }

    @Test
    void sendPrivateMessageEntryDoesNotStartLocalTransactionBeforeRemoteCalls() throws NoSuchMethodException {
        Method method = PrivateMessageAppService.class.getMethod("sendMessage", ImPrivateMessageSendCmd.class);

        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void privateMessageSentEventAttemptsNotificationWithoutAccountOnlineFilter() {
        RecordingNotifierInvoker notifierInvoker = new RecordingNotifierInvoker();
        PrivateMessageAppService appService = new PrivateMessageAppService(
                null,
                null,
                new TestAccountAdapter(false),
                null,
                null,
                null,
                null,
                notifierInvoker,
                null);
        ImPrivateMessageSentEvent event = new ImPrivateMessageSentEvent();
        event.setReceiverId(2L);
        event.setReceiverChatId(102L);
        event.setSenderId(1L);
        event.setMessageId(900L);

        appService.onMessageSent(event);

        assertThat(notifierInvoker.privateSentCommands)
                .extracting(ImPrivateSentNotification::receiverId)
                .containsExactly(2L);
    }

    @Test
    void queryPrivateMessageDetailReturnsOnlyCurrentUsersMessageCopy() {
        MemoryPrivateChatRepository privateChatRepository = new MemoryPrivateChatRepository();
        privateChatRepository.chats.add(privateChat(102L, 2L, 1L));

        MemoryPrivateInboxRepository inboxRepository = new MemoryPrivateInboxRepository();
        inboxRepository.messages.add(privateMessage(900L, 101L, 1L, 1L, ImPrivateMessageStatus.SENT, null, "token-1"));
        inboxRepository.messages.add(privateMessage(900L, 102L, 2L, 1L, ImPrivateMessageStatus.RECEIVED, null, "token-1"));

        PrivateMessageAppService appService = new PrivateMessageAppService(
                privateChatRepository,
                inboxRepository,
                null,
                new ImChatService(privateChatRepository, null, null),
                null,
                null,
                null,
                null,
                null);

        ImPrivateMessageDTO detail = appService.queryMessageDetail(
                new ImPrivateMessageDetailQuery(102L, 2L, "token-1"));

        assertThat(detail.getMessageId()).isEqualTo(900L);
        assertThat(detail.getSenderId()).isEqualTo(1L);
        assertThat(detail.getReceiverId()).isEqualTo(2L);
        assertThat(detail.getContent()).isEqualTo("hello");
    }

    @Test
    void privateMessageRevokedEventInvokesRevokeNotifier() {
        RecordingNotifierInvoker notifierInvoker = new RecordingNotifierInvoker();
        PrivateMessageAppService appService = new PrivateMessageAppService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                notifierInvoker,
                null);
        com.co.kc.imchat.service.message.domain.message.event.ImPrivateMessageRevokedEvent event =
                new com.co.kc.imchat.service.message.domain.message.event.ImPrivateMessageRevokedEvent();
        event.setReceiverId(2L);
        event.setChatId(102L);
        event.setMessageId(900L);

        appService.onMessageRevoked(event);

        assertThat(notifierInvoker.privateRevokedCommands)
                .extracting(ImPrivateRevokedNotification::receiverId)
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
                new TestAccountAdapter(false, 2L),
                new ImChatService(privateChatRepository, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                new TestSocialAdapter(new MissingFriendRepository(), null, null, null),
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
                new TestAccountAdapter(false, 2L),
                new ImChatService(privateChatRepository, null, null),
                new ImMessageService(null, inboxRepository, new FixedSnowflakeId(1L)),
                new TestSocialAdapter(new OneWayNormalFriendRepository(1L, 2L), null, null, null),
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
        return privateMessage(messageId, chatId, userId, senderId, status, revokeTime, "token-" + messageId);
    }

    private ImPrivateInboxMessage privateMessage(
            Long messageId, Long chatId, Long userId, Long senderId,
            ImPrivateMessageStatus status, LocalDateTime revokeTime, String token) {
        return ImPrivateInboxMessage.builder()
                .id(new ImMessageId(messageId))
                .token(new ImMessageToken(token))
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
        return new Group(new GroupId(groupId), name, true);
    }

    private GroupMember groupMember(Long groupId, Long userId) {
        return new GroupMember(new GroupId(groupId), new UserId(userId));
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
                    .filter(message -> message.getId().value().equals(messageId.value()))
                    .findFirst();
        }

        @Override
        public Optional<ImPrivateInboxMessage> find(ImChatId chatId, ImMessageToken messageToken) {
            return messages.stream()
                    .filter(message -> message.getChatId().equals(chatId))
                    .filter(message -> message.getToken().value().equals(messageToken.value()))
                    .findFirst();
        }

        @Override
        public List<ImPrivateInboxMessage> queryHistory(
                ImChatId imChatId, ImMessageId imLastMessageId, int count, UserId viewer) {
            return messages.stream()
                    .filter(message -> message.getChatId().equals(imChatId))
                    .filter(message -> message.getUserId().equals(viewer))
                    .filter(message -> imLastMessageId == null || message.getId().value() < imLastMessageId.value())
                    .sorted((left, right) -> right.getId().value().compareTo(left.getId().value()))
                    .limit(count)
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<ImPrivateInboxMessage> queryDetail(
                ImChatId chatId, ImMessageToken messageToken, UserId viewer) {
            return messages.stream()
                    .filter(message -> message.getChatId().equals(chatId))
                    .filter(message -> message.getUserId().equals(viewer))
                    .filter(message -> message.getToken().value().equals(messageToken.value()))
                    .findFirst();
        }

        @Override
        public boolean contain(ImChatId chatId, ImMessageToken messageToken) {
            return find(chatId, messageToken).isPresent();
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

    private static class Group {
        private final GroupId id;
        private final String name;
        private final boolean active;

        private Group(GroupId id, String name, boolean active) {
            this.id = id;
            this.name = name;
            this.active = active;
        }

        private GroupId getId() {
            return id;
        }

        private String getName() {
            return name;
        }

        private boolean isDismissed() {
            return !active;
        }

        private void ensureActive() {
            if (!active) {
                throw new NotFoundException("群组不存在");
            }
        }
    }

    private record GroupMember(GroupId groupId, UserId userId) {
        private GroupId getGroupId() {
            return groupId;
        }

        private UserId getUserId() {
            return userId;
        }
    }

    private interface FriendRepository {
        boolean isFriendshipActive(UserId userId, UserId friendUserId);

        List<FriendDisplay> find(UserId userId, List<UserId> friendUserIds);
    }

    private static class MemoryGroupRepository {
        private final List<Group> groups = new ArrayList<>();

        public Optional<Group> find(GroupId groupId) {
            return groups.stream()
                    .filter(group -> group.getId().equals(groupId))
                    .findFirst();
        }

        public List<Group> find(List<GroupId> groupIds) {
            return groups.stream()
                    .filter(group -> groupIds.contains(group.getId()))
                    .collect(Collectors.toList());
        }
    }

    private static class MemoryGroupMemberRepository {
        private final List<GroupMember> members = new ArrayList<>();

        public List<GroupMember> find(GroupId groupId) {
            return members.stream()
                    .filter(member -> member.getGroupId().equals(groupId))
                    .collect(Collectors.toList());
        }

        public Optional<GroupMember> find(GroupId groupId, UserId userId) {
            return members.stream()
                    .filter(member -> member.getGroupId().equals(groupId))
                    .filter(member -> member.getUserId().equals(userId))
                    .findFirst();
        }

        public boolean contain(GroupId groupId, UserId userId) {
            return find(groupId, userId).isPresent();
        }

        public void remove(GroupMember groupMember) {
            members.removeIf(member -> member.getGroupId().equals(groupMember.getGroupId())
                    && member.getUserId().equals(groupMember.getUserId()));
        }
    }

    private static class NormalFriendRepository implements FriendRepository {
        @Override
        public List<FriendDisplay> find(UserId userId, List<UserId> friendUserIds) {
            return friendUserIds.stream()
                    .map(friendUserId -> new FriendDisplay(friendUserId.value(), "friend-" + friendUserId.value()))
                    .toList();
        }

        @Override
        public boolean isFriendshipActive(UserId userId, UserId friendUserId) {
            return true;
        }
    }

    private static class MissingFriendRepository extends NormalFriendRepository {
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
        public boolean isFriendshipActive(UserId userId, UserId friendUserId) {
            return this.userId.equals(userId) && this.friendUserId.equals(friendUserId);
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

    private static class TestAccountAdapter extends AccountAdapter {
        private final boolean chatting;
        private final Set<Long> onlineUserIds;

        TestAccountAdapter(boolean chatting, Long... onlineUserIds) {
            super(unusedRemoteService(com.co.kc.imchat.service.account.facade.AccountService.class),
                    unusedRemoteService(com.co.kc.imchat.service.account.facade.AccountSessionService.class));
            this.chatting = chatting;
            this.onlineUserIds = Set.of(onlineUserIds);
        }

        @Override
        public com.co.kc.imchat.service.message.domain.account.model.AuthenticatedUser authenticate(String token) {
            return null;
        }

        @Override
        public void enterChat(Long userId, Long chatId) {
        }

        @Override
        public void exitChat(Long userId) {
        }

        @Override
        public boolean isOnline(Long userId) {
            return onlineUserIds.contains(userId);
        }

        @Override
        public boolean isChatting(Long userId, Long chatId) {
            return chatting;
        }
    }

    private static class ChattingFailureAccountAdapter extends TestAccountAdapter {
        ChattingFailureAccountAdapter() {
            super(false, 2L);
        }

        @Override
        public boolean isChatting(Long userId, Long chatId) {
            throw new IllegalStateException("session unavailable");
        }
    }

    private static class TestSocialAdapter extends SocialAdapter {
        private final FriendRepository friendRepository;
        private final MemoryGroupRepository groupRepository;
        private final MemoryGroupMemberRepository groupMemberRepository;
        private final ImGroupChatRepository groupChatRepository;

        TestSocialAdapter(FriendRepository friendRepository,
                         MemoryGroupRepository groupRepository,
                         MemoryGroupMemberRepository groupMemberRepository,
                         ImGroupChatRepository groupChatRepository) {
            super(unusedRemoteService(com.co.kc.imchat.service.social.facade.SocialService.class));
            this.friendRepository = friendRepository;
            this.groupRepository = groupRepository;
            this.groupMemberRepository = groupMemberRepository;
            this.groupChatRepository = groupChatRepository;
        }

        @Override
        public void ensureFriendshipActive(Long userId, Long peerUserId) {
            if (friendRepository == null) {
                return;
            }
            boolean active = friendRepository.isFriendshipActive(new UserId(userId), new UserId(peerUserId))
                    && friendRepository.isFriendshipActive(new UserId(peerUserId), new UserId(userId));
            if (!active) {
                throw new NotFoundException("好友不存在");
            }
        }

        @Override
        public List<FriendDisplay> getFriendDisplays(Long userId, List<Long> friendUserIds) {
            if (friendRepository == null) {
                return Collections.emptyList();
            }
            return friendRepository.find(new UserId(userId), friendUserIds.stream().map(UserId::new).toList());
        }

        @Override
        public List<GroupSummary> getGroupSummaries(List<Long> groupIds) {
            if (groupRepository == null) {
                return Collections.emptyList();
            }
            return groupIds.stream()
                    .map(GroupId::new)
                    .map(groupRepository::find)
                    .flatMap(Optional::stream)
                    .map(group -> new GroupSummary(group.getId().value(), group.getName(), !group.isDismissed()))
                    .toList();
        }

        @Override
        public void ensureGroupMember(Long groupId, Long userId) {
            if (groupRepository == null || groupMemberRepository == null) {
                return;
            }
            Group group = groupRepository.find(new GroupId(groupId))
                    .orElseThrow(() -> new NotFoundException("群组不存在"));
            group.ensureActive();
            if (!groupMemberRepository.contain(new GroupId(groupId), new UserId(userId))) {
                throw new NotFoundException("群成员不存在");
            }
        }

        @Override
        public List<GroupMessageRecipient> getGroupMessageRecipients(Long groupId) {
            if (groupMemberRepository == null || groupChatRepository == null) {
                return Collections.emptyList();
            }
            List<UserId> memberIds = groupMemberRepository.find(new GroupId(groupId)).stream()
                    .map(GroupMember::getUserId)
                    .toList();
            return groupChatRepository.find(new GroupId(groupId), memberIds).stream()
                    .map(chat -> new GroupMessageRecipient(chat.getUserId().value()))
                    .toList();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T unusedRemoteService(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException("unused remote service");
                });
    }

    private static class RecordingNotifierInvoker extends ImMessageNotifierInvoker {
        private final List<ImPrivateSentNotification> privateSentCommands = new ArrayList<>();
        private final List<ImPrivateRevokedNotification> privateRevokedCommands = new ArrayList<>();

        RecordingNotifierInvoker() {
            super(null, null);
        }

        @Override
        public <T> void invoke(T command) {
            if (command instanceof ImPrivateSentNotification) {
                privateSentCommands.add((ImPrivateSentNotification) command);
            }
            if (command instanceof ImPrivateRevokedNotification) {
                privateRevokedCommands.add((ImPrivateRevokedNotification) command);
            }
        }
    }

    private static class RecordingConfirmableStore implements ImMessageConfirmableStore {
        private final List<String> confirmedReceiptIds = new ArrayList<>();

        @Override
        public void startConfirming(Consumer<ReceiptTask> consumer) {
        }

        @Override
        public void stopConfirming() {
        }

        @Override
        public void offer(ReceiptTask message) {
        }

        @Override
        public void confirm(String receiptId) {
            confirmedReceiptIds.add(receiptId);
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
