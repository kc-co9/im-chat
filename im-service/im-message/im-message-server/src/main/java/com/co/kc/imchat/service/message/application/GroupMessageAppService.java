package com.co.kc.imchat.service.message.application;

import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommit;
import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.service.message.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.service.message.domain.chat.service.ImChatService;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.service.message.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageRevocation;
import com.co.kc.imchat.service.message.domain.message.event.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.service.message.domain.message.event.ImGroupMessageSentEvent;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageTransmission;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageContent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageRecipient;
import com.co.kc.imchat.service.message.domain.message.service.ImMessageService;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageSender;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImOutboundMessage;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.model.cqrs.command.group.GroupMessageReceiveCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.group.GroupMessageRevokeCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.group.GroupMessageReadCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.group.GroupMessageSendCmd;
import com.co.kc.imchat.service.message.application.notification.model.ImGroupRevokedNotification;
import com.co.kc.imchat.service.message.application.notification.model.ImGroupSentNotification;
import com.co.kc.imchat.service.message.model.cqrs.dto.group.GroupMessageDTO;
import com.co.kc.imchat.service.message.model.cqrs.query.group.GroupMessageDetailQuery;
import com.co.kc.imchat.service.message.model.cqrs.query.group.GroupMessageHistoryQuery;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.service.message.application.lock.ImMessageLockScene;
import com.co.kc.imchat.plugin.lock.annotation.DistributeLock;
import com.co.kc.imchat.service.message.application.notification.ImMessageNotifierInvoker;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.message.transformer.ImMessageAppTransformer;
import com.co.kc.imchat.service.message.domain.chat.repository.ImChatViewRepository;
import com.co.kc.imchat.service.message.domain.social.model.GroupMessageRecipient;
import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class GroupMessageAppService {
    private final ImGroupChatRepository imGroupChatRepository;
    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;

    private final ImChatViewRepository imChatViewRepository;
    private final SocialAdapter socialAdapter;
    private final ImMessageService imMessageService;
    private final ImChatService imChatService;

    private final SnowflakeId snowflakeId;
    private final ImMessageNotifierInvoker imMessageNotifierInvoker;
    private final DomainEventPublisher imMessageEventPublisher;
    private final TransactionOperations transactionOperations;

    public GroupMessageAppService(ImGroupChatRepository imGroupChatRepository,
                                  ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                  ImChatViewRepository imChatViewRepository,
                                  SocialAdapter socialAdapter,
                                  ImMessageService imMessageService,
                                  ImChatService imChatService,
                                  SnowflakeId snowflakeId,
                                  ImMessageNotifierInvoker imMessageNotifierInvoker,
                                  DomainEventPublisher imMessageEventPublisher,
                                  TransactionOperations transactionOperations) {
        this.imGroupChatRepository = imGroupChatRepository;
        this.imGroupInboxMessageRepository = imGroupInboxMessageRepository;
        this.imChatViewRepository = imChatViewRepository;
        this.socialAdapter = socialAdapter;
        this.imMessageService = imMessageService;
        this.imChatService = imChatService;
        this.snowflakeId = snowflakeId;
        this.imMessageNotifierInvoker = imMessageNotifierInvoker;
        this.imMessageEventPublisher = imMessageEventPublisher;
        this.transactionOperations = transactionOperations;
    }

    @DistributeLock(scene = ImMessageLockScene.GROUP_MESSAGE_SEND, key = "#command.chatId() + ':' + #command.messageToken()")
    public void sendMessage(GroupMessageSendCmd command) {
        ImMessageId messageId = new ImMessageId(snowflakeId.next());
        ImChatId senderChatId = new ImChatId(command.chatId());
        UserId senderId = new UserId(command.senderId());
        ImMessageToken messageToken = new ImMessageToken(command.messageToken());
        ImMessageContent messageContent = new ImMessageContent(command.messageType(), command.messageContent());

        ImGroupChat senderChat = imGroupChatRepository.find(senderChatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(senderChat, senderId);

        socialAdapter.ensureGroupMember(senderChat.getGroupId().value(), senderId.value());

        imMessageService.ensureGroupMessageUnique(senderChatId, senderId, messageToken);

        ImMessageSender imMessageSender = new ImMessageSender(senderChat, senderId);
        ImOutboundMessage outboundMessage = new ImOutboundMessage(messageId, messageToken, messageContent);
        List<ImMessageRecipient> recipients = findMessageRecipients(senderChat.getGroupId());
        ImGroupMessageTransmission transmission =
                imMessageService.transmitGroupMessage(outboundMessage, imMessageSender, recipients);

        transactionOperations.executeWithoutResult(status -> {
            imGroupInboxMessageRepository.save(transmission.inboxMessages());
            imGroupChatRepository.save(transmission.groupChats());

            ImGroupMessageSentEvent event =
                    imMessageService.newImMessageSentEvent(senderChat.getGroupId(), transmission.getSenderMessage(senderId));
            imMessageEventPublisher.publish(event);
        });
    }

    @AfterTransactionCommit
    public void onMessageSent(ImGroupMessageSentEvent event) {
        GroupId groupId = new GroupId(event.getGroupId());
        UserId senderId = new UserId(event.getSenderId());
        List<ImGroupChat> memberChats = findRecipientChats(groupId);
        for (ImGroupChat memberChat : memberChats) {
            if (memberChat.belongsTo(senderId)) {
                continue;
            }
            ImGroupSentNotification notification =
                    ImMessageAppTransformer.INSTANCE.imGroupSentNotificationFrom(
                            memberChat.getUserId().value(), memberChat.getId().value(), event);
            imMessageNotifierInvoker.invoke(notification);
        }
    }

    @DistributeLock(scene = ImMessageLockScene.GROUP_MESSAGE_REVOKE, key = "#command.chatId() + ':' + #command.messageId()")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void revokeMessage(GroupMessageRevokeCmd command) {
        UserId userId = new UserId(command.userId());
        ImChatId chatId = new ImChatId(command.chatId());
        ImMessageId messageId = new ImMessageId(command.messageId());

        ImGroupChat senderChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(senderChat, userId);

        socialAdapter.ensureGroupMember(senderChat.getGroupId().value(), userId.value());

        ImGroupInboxMessage senderInboxMessage = imGroupInboxMessageRepository.find(chatId, userId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        List<ImGroupInboxMessage> inboxMessages =
                imGroupInboxMessageRepository.findByGroupIdAndMessageId(senderChat.getGroupId(), messageId);
        ImGroupMessageRevocation revocation =
                imMessageService.revokeGroupMessage(inboxMessages, senderInboxMessage, userId);
        imGroupInboxMessageRepository.save(revocation.messages());

        ImGroupMessageRevokedEvent event =
                imMessageService.newImMessageRevokedEvent(senderChat.getGroupId(), revocation.senderMessage());
        imMessageEventPublisher.publish(event);
    }

    @AfterTransactionCommit
    public void onMessageRevoked(ImGroupMessageRevokedEvent event) {
        GroupId groupId = new GroupId(event.getGroupId());
        List<ImGroupChat> memberChats = findRecipientChats(groupId);
        for (ImGroupChat memberChat : memberChats) {
            ImGroupRevokedNotification notification =
                    ImMessageAppTransformer.INSTANCE.imGroupRevokedNotificationFrom(
                            memberChat.getUserId().value(), memberChat.getId().value(), event);
            imMessageNotifierInvoker.invoke(notification);
        }
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void receiveMessage(GroupMessageReceiveCmd command) {
        UserId userId = new UserId(command.userId());
        ImChatId chatId = new ImChatId(command.chatId());
        ImMessageId messageId = new ImMessageId(command.messageId());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        socialAdapter.ensureGroupMember(groupChat.getGroupId().value(), userId.value());

        ImGroupInboxMessage message = imGroupInboxMessageRepository.find(chatId, userId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        message.receive(userId);
        imGroupInboxMessageRepository.save(message);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void readMessage(GroupMessageReadCmd command) {
        UserId userId = new UserId(command.userId());
        ImChatId chatId = new ImChatId(command.chatId());
        ImMessageId messageId = new ImMessageId(command.messageId());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        socialAdapter.ensureGroupMember(groupChat.getGroupId().value(), userId.value());

        ImGroupInboxMessage message = imGroupInboxMessageRepository.find(chatId, userId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        groupChat.readMessage(message);

        imGroupInboxMessageRepository.save(message);
        imGroupChatRepository.save(groupChat);
    }

    public List<GroupMessageDTO> queryHistoryMessage(GroupMessageHistoryQuery query) {
        UserId userId = new UserId(query.userId());
        ImChatId chatId = new ImChatId(query.chatId());
        ImMessageId lastMessageId = FunctionUtils.mappingOrNull(query.lastMessageId(), ImMessageId::new);

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        socialAdapter.ensureGroupMember(groupChat.getGroupId().value(), userId.value());

        List<ImGroupInboxMessage> messageList =
                imGroupInboxMessageRepository.queryHistory(chatId, userId, lastMessageId, query.count());
        return ImMessageAppTransformer.INSTANCE.groupMessageDtoListFrom(messageList);
    }

    public GroupMessageDTO queryMessageDetail(GroupMessageDetailQuery query) {
        UserId userId = new UserId(query.userId());
        ImChatId chatId = new ImChatId(query.chatId());
        ImMessageToken messageToken = new ImMessageToken(query.messageToken());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        socialAdapter.ensureGroupMember(groupChat.getGroupId().value(), userId.value());

        ImGroupInboxMessage message = imGroupInboxMessageRepository.queryDetail(chatId, userId, messageToken);
        if (message == null) {
            throw new NotFoundException("消息不存在");
        }
        return ImMessageAppTransformer.INSTANCE.groupMessageDtoFrom(message);
    }

    private List<ImMessageRecipient> findMessageRecipients(GroupId groupId) {
        return findRecipientChats(groupId).stream()
                .map(chat -> new ImMessageRecipient(chat, imChatViewRepository.isViewing(chat.getUserId(), chat.getId())))
                .toList();
    }

    private List<ImGroupChat> findRecipientChats(GroupId groupId) {
        List<GroupMessageRecipient> recipients = socialAdapter.getGroupMessageRecipients(groupId.value());
        List<UserId> userIds = FunctionUtils.mappingDistinctList(recipients,
                recipient -> new UserId(recipient.userId()));
        List<ImGroupChat> chats = imGroupChatRepository.find(groupId, userIds);
        Map<UserId, ImGroupChat> chatMap = FunctionUtils.mappingMap(chats, ImGroupChat::getUserId, Function.identity());
        return recipients.stream()
                .map(recipient -> findRecipientChat(recipient, groupId, chatMap))
                .toList();
    }

    private ImGroupChat findRecipientChat(GroupMessageRecipient recipient, GroupId groupId, Map<UserId, ImGroupChat> chatMap) {
        ImGroupChat chat = chatMap.get(new UserId(recipient.userId()));
        if (chat == null || !chat.getGroupId().equals(groupId)) {
            throw new NotFoundException("群成员会话不存在");
        }
        return chat;
    }

}
