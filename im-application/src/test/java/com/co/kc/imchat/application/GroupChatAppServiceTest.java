package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.chat.service.ImChatService;
import com.co.kc.imchat.domain.chat.model.ImChatStatus;
import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.chat.repository.ImGroupChatRepository;
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
import com.co.kc.imchat.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.event.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.domain.message.event.ImGroupMessageSentEvent;
import com.co.kc.imchat.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.model.ImMessage;
import com.co.kc.imchat.domain.message.model.ImMessageContent;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.message.service.ImMessageService;
import com.co.kc.imchat.domain.message.model.ImMessageToken;
import com.co.kc.imchat.domain.message.model.ImMessageType;
import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;
import com.co.kc.imchat.domain.session.model.Session;
import com.co.kc.imchat.domain.session.repository.SessionRepository;
import com.co.kc.imchat.domain.shared.event.DomainEvent;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.service.UserService;
import com.co.kc.imchat.common.identity.snowflake.ISnowflakeMachineId;
import com.co.kc.imchat.application.model.cqrs.command.chat.ImGroupChatOpenCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupChatHideCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageReceiveCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageReadCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageRevokeCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageSendCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupSentNotifyCmd;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupChatOpenDTO;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupMessageDTO;
import com.co.kc.imchat.application.model.cqrs.query.group.GroupMessageDetailQuery;
import com.co.kc.imchat.application.model.cqrs.query.group.GroupMessageHistoryQuery;
import com.co.kc.imchat.domain.user.service.PasswordService;
import com.co.kc.imchat.application.support.event.DomainEventPublisher;
import com.co.kc.imchat.common.exception.BusinessException;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.application.support.notifier.ImMessageNotifierInvoker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupChatAppServiceTest {

    @Test
    void sendGroupMessageRejectsChatIdOwnedByAnotherMember() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));

        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, normalGroupRepository(1001L));
        GroupMessageSendCmd command = groupMessageSendCmd(102L, 1L);

        assertThatThrownBy(() -> appService.sendMessage(command))
                .isInstanceOf(BusinessException.class);
        assertThat(inboxRepository.savedMessages).isEmpty();
        assertThat(groupChatRepository.savedGroupChats).isEmpty();
    }

    @Test
    void openGroupChatEntersExistingChatByChatId() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 2L));

        SignedInSessionRepository sessionRepository = new SignedInSessionRepository();
        ChatAppService appService = chatAppService(
                groupChatRepository,
                groupMemberRepository,
                new MemoryGroupInboxRepository(),
                normalGroupRepository(1001L),
                sessionRepository);
        ImGroupChatOpenCmd command = new ImGroupChatOpenCmd(2L, 102L);

        GroupChatOpenDTO result = appService.openGroupChat(command);

        assertThat(result.getChatId()).isEqualTo(102L);
        assertThat(result.getGroupId()).isEqualTo(1001L);
        assertThat(groupChatRepository.savedGroupChats).hasSize(1);

        ImGroupChat savedChat = groupChatRepository.findSavedByUserId(2L);
        assertThat(savedChat.getId().getValue()).isEqualTo(102L);
        assertThat(savedChat.getGroupId().getValue()).isEqualTo(1001L);
        assertThat(savedChat.getUnreadMessageCount()).isZero();
        assertThat(savedChat.getStatus()).isEqualTo(ImChatStatus.NORMAL);
        assertThat(savedChat.getActiveTime()).isNotNull();
        assertThat(sessionRepository.session.getChatId().getValue()).isEqualTo(102L);
    }

    @Test
    void openGroupChatShowsHiddenChatAndUpdatesActiveTime() {
        LocalDateTime oldActiveTime = LocalDateTime.of(2026, 1, 1, 10, 0);
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        ImGroupChat groupChat = groupChat(102L, 1001L, 2L);
        groupChat.hide();
        groupChat.setActiveTime(oldActiveTime);

        groupChatRepository.groupChats.add(groupChat);

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 2L));

        ChatAppService appService = chatAppService(
                groupChatRepository,
                groupMemberRepository,
                new MemoryGroupInboxRepository(),
                normalGroupRepository(1001L),
                new SignedInSessionRepository());

        appService.openGroupChat(new ImGroupChatOpenCmd(2L, 102L));

        ImGroupChat savedChat = groupChatRepository.findSavedByUserId(2L);
        assertThat(savedChat.getStatus()).isEqualTo(ImChatStatus.NORMAL);
        assertThat(savedChat.getActiveTime()).isAfter(oldActiveTime);
    }

    @Test
    void hideGroupChatHidesOnlyCurrentUserChat() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        MemoryGroupRepository groupRepository = normalGroupRepository(1001L);
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));

        ChatAppService appService = new ChatAppService(
                null,
                groupChatRepository,
                groupRepository,
                new GroupService(groupMemberRepository, groupChatRepository, groupRepository, null, null),
                null,
                new ImChatService(null, null, null, groupChatRepository, null, null),
                null,
                null);

        appService.hideGroupChat(new GroupChatHideCmd(1L, 101L));

        assertThat(groupChatRepository.savedGroupChats).hasSize(1);

        ImGroupChat savedChat = groupChatRepository.savedGroupChats.get(0);
        assertThat(savedChat.getStatus()).isEqualTo(ImChatStatus.HIDDEN);
    }

    @Test
    void openGroupChatRejectsDismissedGroup() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 2L));

        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(dismissedGroup(1001L, 1L, "group"));

        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        ChatAppService appService = chatAppService(
                groupChatRepository,
                groupMemberRepository,
                inboxRepository,
                groupRepository,
                new SignedInSessionRepository());
        ImGroupChatOpenCmd command = new ImGroupChatOpenCmd(2L, 102L);

        assertThatThrownBy(() -> appService.openGroupChat(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("群聊已解散");
        assertThat(groupChatRepository.savedGroupChats).isEmpty();
        assertThat(inboxRepository.savedMessages).isEmpty();
    }

    @Test
    void openGroupChatReturnsNotFoundBeforeCheckingMembership() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));

        ChatAppService appService = chatAppService(
                groupChatRepository,
                new MemoryGroupMemberRepository(),
                new MemoryGroupInboxRepository(),
                new MemoryGroupRepository(),
                new SignedInSessionRepository());

        assertThatThrownBy(() -> appService.openGroupChat(new ImGroupChatOpenCmd(2L, 102L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("群组不存在");
    }

    @Test
    void sendGroupMessageCreatesInboxForEachMemberAndUpdatesReadState() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));

        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, normalGroupRepository(1001L));
        GroupMessageSendCmd command = groupMessageSendCmd(101L, 1L);

        appService.sendMessage(command);

        assertThat(inboxRepository.savedMessages).hasSize(2);
        ImGroupInboxMessage senderMessage = inboxRepository.findSavedByUserId(1L);
        ImGroupInboxMessage receiverMessage = inboxRepository.findSavedByUserId(2L);
        ImGroupChat senderChat = groupChatRepository.findSavedByUserId(1L);
        ImGroupChat receiverChat = groupChatRepository.findSavedByUserId(2L);

        assertThat(senderMessage.getStatus()).isEqualTo(ImGroupMessageStatus.READ);
        assertThat(senderMessage.getReadTime()).isNotNull();
        assertThat(senderMessage.getReceivedTime()).isNotNull();

        assertThat(receiverMessage.getStatus()).isEqualTo(ImGroupMessageStatus.SENT);
        assertThat(receiverMessage.getReceivedTime()).isNull();
        assertThat(receiverMessage.getChatId().getValue()).isEqualTo(102L);

        assertThat(senderChat.getUnreadMessageCount()).isZero();
        assertThat(senderChat.getReadMessageId().getValue()).isEqualTo(900L);
        assertThat(senderChat.getStatus()).isEqualTo(ImChatStatus.NORMAL);
        assertThat(senderChat.getActiveTime()).isEqualTo(senderMessage.getSendTime());

        assertThat(receiverChat.getUnreadMessageCount()).isEqualTo(1);
        assertThat(receiverChat.getReadMessageId()).isNull();
        assertThat(receiverChat.getStatus()).isEqualTo(ImChatStatus.NORMAL);
        assertThat(receiverChat.getActiveTime()).isEqualTo(receiverMessage.getSendTime());
    }

    @Test
    void sendGroupMessageRejectsDismissedGroup() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, dismissedGroupRepository(1001L));
        GroupMessageSendCmd command = groupMessageSendCmd(101L, 1L);

        assertThatThrownBy(() -> appService.sendMessage(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("群聊已解散");
        assertThat(inboxRepository.savedMessages).isEmpty();
    }

    @Test
    void groupMessageSentNotificationSkipsSender() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        groupChatRepository.groupChats.add(groupChat(103L, 1001L, 3L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        groupMemberRepository.members.add(groupMember(1001L, 3L));
        RecordingNotifierInvoker notifierInvoker = new RecordingNotifierInvoker();
        GroupMessageAppService appService = new GroupMessageAppService(
                groupChatRepository,
                new MemoryGroupRepository(),
                groupMemberRepository,
                null,
                new OnlineUserService(2L),
                null,
                null,
                new ImChatService(null, null, null, groupChatRepository, null, null),
                null,
                notifierInvoker,
                null);
        ImGroupMessageSentEvent event = new ImGroupMessageSentEvent();
        event.setGroupId(1001L);
        event.setSenderId(1L);
        event.setMessageId(900L);
        event.setMessageType(ImMessageTypeEnum.TEXT);
        event.setMessageContent("hello");
        event.setSendTime(java.time.LocalDateTime.now());

        appService.onMessageSent(event);

        assertThat(notifierInvoker.groupSentCommands)
                .extracting(GroupSentNotifyCmd::getReceiverId)
                .containsExactly(2L);
        assertThat(notifierInvoker.groupSentCommands)
                .extracting(GroupSentNotifyCmd::getChatId)
                .containsExactly(102L);
    }

    @Test
    void openGroupChatMarksUnreadInboxMessagesRead() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L, 902L, 2));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));

        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        inboxRepository.unreadMessages.add(groupInboxMessage(901L, 101L, 1001L, 1L, 2L));
        inboxRepository.unreadMessages.add(groupInboxMessage(902L, 101L, 1001L, 1L, 2L));

        ChatAppService appService = chatAppService(
                groupChatRepository,
                groupMemberRepository,
                inboxRepository,
                normalGroupRepository(1001L),
                new SignedInSessionRepository());
        ImGroupChatOpenCmd command = new ImGroupChatOpenCmd(1L, 101L);

        GroupChatOpenDTO result = appService.openGroupChat(command);

        assertThat(result.getChatId()).isEqualTo(101L);
        assertThat(result.getGroupId()).isEqualTo(1001L);

        ImGroupChat savedChat = groupChatRepository.findSavedByUserId(1L);
        assertThat(savedChat.getUnreadMessageCount()).isZero();
        assertThat(savedChat.getReadMessageId().getValue()).isEqualTo(902L);

        assertThat(inboxRepository.savedMessages).hasSize(2);
        assertThat(inboxRepository.savedMessages)
                .allSatisfy(message -> {
                    assertThat(message.getStatus()).isEqualTo(ImGroupMessageStatus.READ);
                    assertThat(message.getReadTime()).isNotNull();
                });
    }

    @Test
    void revokeGroupMessageRevokesSenderInboxOnlyOnce() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        ImGroupInboxMessage senderMessage = groupInboxMessage(900L, 101L, 1001L, 1L, 1L);
        ImGroupInboxMessage receiverMessage = groupInboxMessage(900L, 102L, 1001L, 2L, 1L);
        inboxRepository.messages.add(senderMessage);
        inboxRepository.messages.add(receiverMessage);
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, normalGroupRepository(1001L));
        GroupMessageRevokeCmd command = groupMessageRevokeCmd(101L, 1L, 900L);

        appService.revokeMessage(command);

        assertThat(inboxRepository.savedMessages).hasSize(2);
        assertThat(inboxRepository.savedMessages)
                .allSatisfy(message -> assertThat(message.getStatus()).isEqualTo(ImGroupMessageStatus.REVOKED));
    }

    @Test
    void revokeGroupMessageRevokesAllInboxCopiesWhenRepositoryReturnsDetachedInstances() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        MemoryDomainEventPublisher eventPublisher = new MemoryDomainEventPublisher();
        inboxRepository.messages.add(groupInboxMessage(900L, 101L, 1001L, 1L, 1L));
        inboxRepository.groupMessageCopies.add(groupInboxMessage(900L, 101L, 1001L, 1L, 1L));
        inboxRepository.groupMessageCopies.add(groupInboxMessage(900L, 102L, 1001L, 2L, 1L));
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, normalGroupRepository(1001L), eventPublisher);

        appService.revokeMessage(groupMessageRevokeCmd(101L, 1L, 900L));

        assertThat(inboxRepository.savedMessages).hasSize(2);
        assertThat(inboxRepository.savedMessages)
                .extracting(message -> message.getUserId().getValue(), ImGroupInboxMessage::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, ImGroupMessageStatus.REVOKED),
                        org.assertj.core.groups.Tuple.tuple(2L, ImGroupMessageStatus.REVOKED));
        assertThat(inboxRepository.savedMessages)
                .allSatisfy(message -> assertThat(message.getRevokeTime()).isNotNull());
        ImGroupMessageRevokedEvent event = (ImGroupMessageRevokedEvent) eventPublisher.events.get(0);
        assertThat(event.getRevokeTime()).isNotNull();
    }

    @Test
    void revokeGroupMessageRejectsOtherMemberChatId() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, normalGroupRepository(1001L));
        GroupMessageRevokeCmd command = groupMessageRevokeCmd(102L, 1L, 900L);

        assertThatThrownBy(() -> appService.revokeMessage(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法开启聊天");
        assertThat(inboxRepository.savedMessages).isEmpty();
    }

    @Test
    void revokeGroupMessageRejectsDismissedGroup() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        inboxRepository.messages.add(groupInboxMessage(900L, 101L, 1001L, 1L, 1L));
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, dismissedGroupRepository(1001L));
        GroupMessageRevokeCmd command = groupMessageRevokeCmd(101L, 1L, 900L);

        assertThatThrownBy(() -> appService.revokeMessage(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("群聊已解散");
        assertThat(inboxRepository.savedMessages).isEmpty();
    }

    @Test
    void receiveGroupMessageMarksInboxMessageReceivedOnly() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L, 900L, 1));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));

        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        inboxRepository.messages.add(groupInboxMessage(900L, 101L, 1001L, 1L, 2L));

        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository,
                groupMemberRepository,
                inboxRepository,
                normalGroupRepository(1001L));
        GroupMessageReceiveCmd command = groupMessageReceiveCmd(101L, 1L, 900L);

        appService.receiveMessage(command);

        ImGroupInboxMessage savedMessage = inboxRepository.findSavedByUserId(1L);
        assertThat(savedMessage.getStatus()).isEqualTo(ImGroupMessageStatus.RECEIVED);
        assertThat(savedMessage.getReceivedTime()).isNotNull();
        assertThat(groupChatRepository.savedGroupChats).isEmpty();
    }

    @Test
    void receiveGroupMessageRejectsOtherMemberChatId() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L, 900L, 1));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));

        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        inboxRepository.messages.add(groupInboxMessage(900L, 102L, 1001L, 2L, 1L));

        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository,
                groupMemberRepository,
                inboxRepository,
                normalGroupRepository(1001L));
        GroupMessageReceiveCmd command = groupMessageReceiveCmd(102L, 1L, 900L);

        assertThatThrownBy(() -> appService.receiveMessage(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法开启聊天");
        assertThat(inboxRepository.savedMessages).isEmpty();
    }

    @Test
    void readGroupMessageMarksInboxMessageAndChatRead() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L, 900L, 1));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));

        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        inboxRepository.messages.add(groupInboxMessage(900L, 101L, 1001L, 1L, 2L));

        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository,
                groupMemberRepository,
                inboxRepository,
                normalGroupRepository(1001L));
        GroupMessageReadCmd command = groupMessageReadCmd(101L, 1L, 900L);

        appService.readMessage(command);

        ImGroupInboxMessage savedMessage = inboxRepository.findSavedByUserId(1L);
        assertThat(savedMessage.getStatus()).isEqualTo(ImGroupMessageStatus.READ);
        assertThat(savedMessage.getReadTime()).isNotNull();

        ImGroupChat savedChat = groupChatRepository.findSavedByUserId(1L);
        assertThat(savedChat.getReadMessageId().getValue()).isEqualTo(900L);
        assertThat(savedChat.getUnreadMessageCount()).isZero();
    }

    @Test
    void readGroupMessageRejectsOtherMemberChatId() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L, 900L, 1));

        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));

        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        inboxRepository.messages.add(groupInboxMessage(900L, 102L, 1001L, 2L, 1L));

        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository,
                groupMemberRepository,
                inboxRepository,
                normalGroupRepository(1001L));
        GroupMessageReadCmd command = groupMessageReadCmd(102L, 1L, 900L);

        assertThatThrownBy(() -> appService.readMessage(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法开启聊天");
        assertThat(inboxRepository.savedMessages).isEmpty();
        assertThat(groupChatRepository.savedGroupChats).isEmpty();
    }

    @Test
    void queryGroupHistoryRejectsDismissedGroup() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, dismissedGroupRepository(1001L));
        GroupMessageHistoryQuery query = new GroupMessageHistoryQuery(101L, 1L, null, 20);

        assertThatThrownBy(() -> appService.queryHistoryMessage(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("群聊已解散");
    }

    @Test
    void queryGroupHistoryRejectsOtherMemberChatId() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, normalGroupRepository(1001L));
        GroupMessageHistoryQuery query = new GroupMessageHistoryQuery(102L, 1L, null, 20);

        assertThatThrownBy(() -> appService.queryHistoryMessage(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法开启聊天");
    }

    @Test
    void queryGroupHistoryReturnsNotFoundBeforeCheckingMembership() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));

        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository,
                new MemoryGroupMemberRepository(),
                new MemoryGroupInboxRepository(),
                new MemoryGroupRepository());

        assertThatThrownBy(() -> appService.queryHistoryMessage(new GroupMessageHistoryQuery(101L, 1L, null, 20)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("群组不存在");
    }

    @Test
    void queryGroupHistoryKeepsRevokedMessagesWithHiddenContent() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        inboxRepository.messages.add(groupInboxMessage(902L, 101L, 1001L, 1L, 2L, ImGroupMessageStatus.SENT));
        inboxRepository.messages.add(groupInboxMessage(
                901L, 101L, 1001L, 1L, 2L, ImGroupMessageStatus.REVOKED, LocalDateTime.now().minusMinutes(1)));
        inboxRepository.messages.add(groupInboxMessage(
                900L, 101L, 1001L, 1L, 2L, ImGroupMessageStatus.REVOKED, LocalDateTime.now().minusMinutes(3)));
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, normalGroupRepository(1001L));
        GroupMessageHistoryQuery query = new GroupMessageHistoryQuery(101L, 1L, null, 20);

        List<GroupMessageDTO> messages = appService.queryHistoryMessage(query);

        assertThat(messages)
                .extracting(GroupMessageDTO::getMessageId)
                .containsExactly(902L, 901L, 900L);
        assertThat(messages)
                .extracting(GroupMessageDTO::getContent)
                .containsExactly("hello", null, null);
    }

    @Test
    void queryGroupMessageDetailThrowsWhenMessageMissing() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository,
                groupMemberRepository,
                new MemoryGroupInboxRepository(),
                normalGroupRepository(1001L));
        GroupMessageDetailQuery query = new GroupMessageDetailQuery(101L, 1L, "missing");

        assertThatThrownBy(() -> appService.queryMessageDetail(query))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void queryGroupMessageDetailRejectsDismissedGroup() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(101L, 1001L, 1L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, dismissedGroupRepository(1001L));
        GroupMessageDetailQuery query = new GroupMessageDetailQuery(101L, 1L, "token-1");

        assertThatThrownBy(() -> appService.queryMessageDetail(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("群聊已解散");
    }

    @Test
    void queryGroupMessageDetailRejectsOtherMemberChatId() {
        MemoryGroupChatRepository groupChatRepository = new MemoryGroupChatRepository();
        groupChatRepository.groupChats.add(groupChat(102L, 1001L, 2L));
        MemoryGroupMemberRepository groupMemberRepository = new MemoryGroupMemberRepository();
        groupMemberRepository.members.add(groupMember(1001L, 1L));
        groupMemberRepository.members.add(groupMember(1001L, 2L));
        MemoryGroupInboxRepository inboxRepository = new MemoryGroupInboxRepository();
        GroupMessageAppService appService = groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, normalGroupRepository(1001L));
        GroupMessageDetailQuery query = new GroupMessageDetailQuery(102L, 1L, "token-1");

        assertThatThrownBy(() -> appService.queryMessageDetail(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法开启聊天");
    }

    private ImGroupChat groupChat(Long chatId, Long groupId, Long userId) {
        return groupChat(chatId, groupId, userId, null, 0);
    }

    private ImGroupChat groupChat(Long chatId, Long groupId, Long userId, Long lastMessageId, int unreadMessageCount) {
        return ImGroupChat.builder()
                .id(new ImChatId(chatId))
                .groupId(new GroupId(groupId))
                .userId(new UserId(userId))
                .type(ImChatType.GROUP)
                .lastMessageId(lastMessageId == null ? null : new ImMessageId(lastMessageId))
                .unreadMessageCount(unreadMessageCount)
                .build();
    }

    private ImGroupInboxMessage groupInboxMessage(
            Long messageId, Long chatId, Long groupId, Long userId, Long senderId) {
        return groupInboxMessage(messageId, chatId, groupId, userId, senderId, ImGroupMessageStatus.SENT);
    }

    private ImGroupInboxMessage groupInboxMessage(
            Long messageId, Long chatId, Long groupId, Long userId, Long senderId, ImGroupMessageStatus status) {
        return groupInboxMessage(messageId, chatId, groupId, userId, senderId, status, null);
    }

    private ImGroupInboxMessage groupInboxMessage(
            Long messageId, Long chatId, Long groupId, Long userId, Long senderId,
            ImGroupMessageStatus status, LocalDateTime revokeTime) {
        return ImGroupInboxMessage.builder()
                .id(new ImMessageId(messageId))
                .token(new ImMessageToken("token-" + messageId))
                .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                .chatId(new ImChatId(chatId))
                .groupId(new GroupId(groupId))
                .userId(new UserId(userId))
                .senderId(new UserId(senderId))
                .status(status)
                .sendTime(LocalDateTime.now())
                .revokeTime(revokeTime)
                .build();
    }

    private GroupMember groupMember(Long groupId, Long userId) {
        GroupId imGroupId = new GroupId(groupId);
        UserId imUserId = new UserId(userId);
        return GroupMember.builder()
                .id(new MemberId(imGroupId, imUserId))
                .groupId(imGroupId)
                .userId(imUserId)
                .joinTime(java.time.LocalDateTime.now())
                .build();
    }

    private Group group(Long groupId, Long ownerId, String name) {
        return group(groupId, ownerId, name, GroupStatus.ACTIVE);
    }

    private Group group(Long groupId, Long ownerId, String name, GroupStatus status) {
        return Group.builder()
                .id(new GroupId(groupId))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(ownerId))
                .name(new GroupName(name))
                .memberCount(new MemberCount(1))
                .status(status)
                .build();
    }

    private Group dismissedGroup(Long groupId, Long ownerId, String name) {
        return group(groupId, ownerId, name, GroupStatus.DISMISSED);
    }

    private ChatAppService chatAppService(MemoryGroupChatRepository groupChatRepository,
                                          MemoryGroupMemberRepository groupMemberRepository,
                                          MemoryGroupInboxRepository inboxRepository,
                                          MemoryGroupRepository groupRepository,
                                          SessionRepository sessionRepository) {
        return new ChatAppService(
                null,
                groupChatRepository,
                groupRepository,
                new GroupService(groupMemberRepository, groupChatRepository, groupRepository, null, null),
                inboxRepository,
                new ImChatService(null, null, null, null, sessionRepository, null),
                null,
                new ImMessageService(inboxRepository, null, null));
    }

    private GroupMessageAppService groupMessageAppService(MemoryGroupChatRepository groupChatRepository,
                                                          MemoryGroupMemberRepository groupMemberRepository,
                                                          MemoryGroupInboxRepository inboxRepository,
                                                          MemoryGroupRepository groupRepository) {
        return groupMessageAppService(
                groupChatRepository, groupMemberRepository, inboxRepository, groupRepository, new NoopDomainEventPublisher());
    }

    private GroupMessageAppService groupMessageAppService(MemoryGroupChatRepository groupChatRepository,
                                                          MemoryGroupMemberRepository groupMemberRepository,
                                                          MemoryGroupInboxRepository inboxRepository,
                                                          MemoryGroupRepository groupRepository,
                                                          DomainEventPublisher eventPublisher) {
        return new GroupMessageAppService(
                groupChatRepository,
                groupRepository,
                groupMemberRepository,
                inboxRepository,
                new NonChattingUserService(),
                new GroupService(groupMemberRepository, groupChatRepository, groupRepository, null, null),
                new ImMessageService(inboxRepository, null, new FixedSnowflakeId(1L)),
                new ImChatService(null, null, null, groupChatRepository, null, null),
                new FixedSnowflakeId(900L),
                null,
                eventPublisher);
    }

    private GroupMessageSendCmd groupMessageSendCmd(Long chatId, Long senderId) {
        GroupMessageSendCmd command = new GroupMessageSendCmd();
        command.setChatId(chatId);
        command.setSenderId(senderId);
        command.setMessageToken("token-1");
        command.setMessageType(ImMessageType.TEXT);
        command.setMessageContent("hello");
        return command;
    }

    private GroupMessageRevokeCmd groupMessageRevokeCmd(Long chatId, Long userId, Long messageId) {
        GroupMessageRevokeCmd command = new GroupMessageRevokeCmd();
        command.setChatId(chatId);
        command.setUserId(userId);
        command.setMessageId(messageId);
        return command;
    }

    private GroupMessageReceiveCmd groupMessageReceiveCmd(Long chatId, Long userId, Long messageId) {
        GroupMessageReceiveCmd command = new GroupMessageReceiveCmd();
        command.setChatId(chatId);
        command.setUserId(userId);
        command.setMessageId(messageId);
        return command;
    }

    private GroupMessageReadCmd groupMessageReadCmd(Long chatId, Long userId, Long messageId) {
        GroupMessageReadCmd command = new GroupMessageReadCmd();
        command.setChatId(chatId);
        command.setUserId(userId);
        command.setMessageId(messageId);
        return command;
    }

    private MemoryGroupRepository normalGroupRepository(Long groupId) {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(group(groupId, 1L, "group"));
        return groupRepository;
    }

    private MemoryGroupRepository dismissedGroupRepository(Long groupId) {
        MemoryGroupRepository groupRepository = new MemoryGroupRepository();
        groupRepository.groups.add(dismissedGroup(groupId, 1L, "group"));
        return groupRepository;
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

    private static class NonChattingUserService extends UserService {
        NonChattingUserService() {
            super(null, new EmptySessionRepository(), (PasswordService) null, null);
        }

        @Override
        public boolean isChatting(ImChatId chatId, UserId receiverId) {
            return false;
        }
    }

    private static class OnlineUserService extends UserService {
        private final List<Long> onlineUserIds;

        OnlineUserService(Long... onlineUserIds) {
            super(null, new EmptySessionRepository(), (PasswordService) null, null);
            this.onlineUserIds = java.util.Arrays.asList(onlineUserIds);
        }

        @Override
        public boolean isOnline(UserId userId) {
            return onlineUserIds.contains(userId.getValue());
        }
    }

    private static class EmptySessionRepository implements SessionRepository {
        @Override
        public Optional<Session> find(UserId userId) {
            return Optional.empty();
        }

        @Override
        public void save(Session session) {
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

    private static class NoopDomainEventPublisher implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {
        }

        @Override
        public void publish(List<DomainEvent> eventList) {
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

    private static class RecordingNotifierInvoker extends ImMessageNotifierInvoker {
        private final List<GroupSentNotifyCmd> groupSentCommands = new ArrayList<>();

        RecordingNotifierInvoker() {
            super(null, null);
        }

        @Override
        public <T> void invoke(T command) {
            if (command instanceof GroupSentNotifyCmd) {
                groupSentCommands.add((GroupSentNotifyCmd) command);
            }
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

    private static class MemoryGroupChatRepository implements ImGroupChatRepository {
        private final List<ImGroupChat> groupChats = new ArrayList<>();
        private List<ImGroupChat> savedGroupChats = new ArrayList<>();

        @Override
        public Optional<ImGroupChat> find(ImChatId chatId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getId().getValue().equals(chatId.getValue()))
                    .findFirst();
        }

        @Override
        public Optional<ImGroupChat> find(GroupId groupId, UserId userId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getGroupId().equals(groupId))
                    .filter(groupChat -> groupChat.getUserId().equals(userId))
                    .findFirst();
        }

        @Override
        public List<ImGroupChat> find(GroupId groupId) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getGroupId().getValue().equals(groupId.getValue()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(java.util.Collection<GroupId> groupIds) {
            return groupChats.stream()
                    .filter(groupChat -> groupIds.contains(groupChat.getGroupId()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ImGroupChat> find(UserId userId, java.util.Collection<GroupId> groupIds) {
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
        public List<ImGroupChat> find(GroupId groupId, List<UserId> memberIds) {
            return groupChats.stream()
                    .filter(groupChat -> groupChat.getGroupId().equals(groupId))
                    .filter(groupChat -> memberIds.contains(groupChat.getUserId()))
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
        public void save(List<ImGroupChat> groupChats) {
            savedGroupChats = new ArrayList<>(groupChats);
        }

        @Override
        public boolean contain(ImChatId chatId, UserId userId) {
            return find(chatId).map(chat -> chat.belongsTo(userId)).orElse(false);
        }

        @Override
        public void remove(GroupId groupId, UserId userId) {
            groupChats.removeIf(groupChat -> groupChat.getGroupId().equals(groupId)
                    && groupChat.getUserId().equals(userId));
        }

        private ImGroupChat findSavedByUserId(Long userId) {
            return savedGroupChats.stream()
                    .filter(groupChat -> groupChat.belongsTo(new UserId(userId)))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
        }
    }

    private static class MemoryGroupMemberRepository implements GroupMemberRepository {
        private final List<GroupMember> members = new ArrayList<>();
        private List<GroupMember> savedMembers = new ArrayList<>();

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
            savedMembers = new ArrayList<>(members);
        }

        @Override
        public void save(GroupMember member) {
            savedMembers = Collections.singletonList(member);
        }

        @Override
        public void remove(GroupMember groupMember) {
            members.removeIf(member -> member.getGroupId().equals(groupMember.getGroupId())
                    && member.getUserId().equals(groupMember.getUserId()));
        }
    }

    private static class MemoryGroupInboxRepository implements ImGroupInboxMessageRepository {
        private final List<ImGroupInboxMessage> messages = new ArrayList<>();
        private final List<ImGroupInboxMessage> groupMessageCopies = new ArrayList<>();
        private List<ImGroupInboxMessage> savedMessages = new ArrayList<>();
        private final List<ImGroupInboxMessage> unreadMessages = new ArrayList<>();

        @Override
        public void save(ImGroupInboxMessage message) {
            savedMessages = Collections.singletonList(message);
        }

        @Override
        public void save(List<ImGroupInboxMessage> messages) {
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
        public List<ImGroupInboxMessage> findByGroupIdAndMessageId(GroupId groupId, ImMessageId messageId) {
            List<ImGroupInboxMessage> source = groupMessageCopies.isEmpty() ? messages : groupMessageCopies;
            return source.stream()
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
        public List<ImGroupInboxMessage> queryHistory(
                ImChatId chatId, UserId userId, ImMessageId lastMessageId, Integer count) {
            return messages.stream()
                    .filter(message -> message.getChatId().equals(chatId))
                    .filter(message -> message.getUserId().equals(userId))
                    .filter(message -> lastMessageId == null || message.getId().getValue() < lastMessageId.getValue())
                    .sorted((left, right) -> right.getId().getValue().compareTo(left.getId().getValue()))
                    .limit(count)
                    .collect(Collectors.toList());
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
