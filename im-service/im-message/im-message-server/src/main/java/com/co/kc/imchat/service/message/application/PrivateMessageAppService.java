package com.co.kc.imchat.service.message.application;

import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommit;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateMessageStatus;
import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.service.message.model.cqrs.query.ImPrivateMessageDetailQuery;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.service.ImChatService;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateMessageRevocation;
import com.co.kc.imchat.service.message.domain.message.event.ImPrivateMessageRevokedEvent;
import com.co.kc.imchat.service.message.domain.message.event.ImPrivateMessageSentEvent;
import com.co.kc.imchat.service.message.domain.message.service.ImMessageService;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageContent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.repository.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageReceiveCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.service.message.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.service.message.application.lock.ImMessageLockScene;
import com.co.kc.imchat.plugin.lock.annotation.DistributeLock;
import com.co.kc.imchat.service.message.application.notification.ImMessageNotifierInvoker;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.message.transformer.ImMessageAppTransformer;
import com.co.kc.imchat.service.message.adapter.account.AccountAdapter;
import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDateTime;
import java.util.List;

public class PrivateMessageAppService {

    private final ImPrivateChatRepository imPrivateChatRepository;
    private final ImPrivateInboxMessageRepository imPrivateInboxMessageRepository;

    private final AccountAdapter accountAdapter;
    private final ImChatService imChatService;
    private final ImMessageService imMessageService;
    private final SocialAdapter socialAdapter;

    private final SnowflakeId snowflakeId;
    private final ImMessageNotifierInvoker imMessageNotifierInvoker;
    private final DomainEventPublisher imMessageEventPublisher;
    private final TransactionOperations transactionOperations;

    public PrivateMessageAppService(ImPrivateChatRepository imPrivateChatRepository,
                                    ImPrivateInboxMessageRepository imPrivateInboxMessageRepository,
                                    AccountAdapter accountAdapter,
                                    ImChatService imChatService,
                                    ImMessageService imMessageService,
                                    SocialAdapter socialAdapter,
                                    SnowflakeId snowflakeId,
                                    ImMessageNotifierInvoker imMessageNotifierInvoker,
                                    DomainEventPublisher imMessageEventPublisher) {
        this(imPrivateChatRepository, imPrivateInboxMessageRepository, accountAdapter, imChatService, imMessageService,
                socialAdapter, snowflakeId, imMessageNotifierInvoker, imMessageEventPublisher,
                immediateTransactionOperations());
    }

    public PrivateMessageAppService(ImPrivateChatRepository imPrivateChatRepository,
                                    ImPrivateInboxMessageRepository imPrivateInboxMessageRepository,
                                    AccountAdapter accountAdapter,
                                    ImChatService imChatService,
                                    ImMessageService imMessageService,
                                    SocialAdapter socialAdapter,
                                    SnowflakeId snowflakeId,
                                    ImMessageNotifierInvoker imMessageNotifierInvoker,
                                    DomainEventPublisher imMessageEventPublisher,
                                    TransactionOperations transactionOperations) {
        this.imPrivateChatRepository = imPrivateChatRepository;
        this.imPrivateInboxMessageRepository = imPrivateInboxMessageRepository;
        this.accountAdapter = accountAdapter;
        this.imChatService = imChatService;
        this.imMessageService = imMessageService;
        this.socialAdapter = socialAdapter;
        this.snowflakeId = snowflakeId;
        this.imMessageNotifierInvoker = imMessageNotifierInvoker;
        this.imMessageEventPublisher = imMessageEventPublisher;
        this.transactionOperations = transactionOperations;
    }

    @DistributeLock(scene = ImMessageLockScene.PRIVATE_MESSAGE_SEND, key = "#command.chatId() + ':' + #command.messageToken()")
    public void sendMessage(ImPrivateMessageSendCmd command) {
        ImMessageId messageId = new ImMessageId(snowflakeId.next());
        UserId userId = new UserId(command.userId());
        ImChatId chatId = new ImChatId(command.chatId());
        ImMessageToken messageToken = new ImMessageToken(command.messageToken());
        ImMessageContent messageContent = new ImMessageContent(command.messageType(), command.messageContent());

        ImPrivateChat senderChat = imPrivateChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(senderChat, userId);

        ImPrivateChat receiverChat = imChatService.getPeerChat(senderChat).orElseThrow(() -> new NotFoundException("接收方会话不存在"));
        imChatService.ensureBelongsTo(receiverChat, senderChat.getPeerUserId());

        socialAdapter.ensureFriendshipActive(senderChat.getUserId().value(), receiverChat.getUserId().value());
        imMessageService.ensurePrivateMessageUnique(chatId, messageToken);
        boolean receiverChatting = accountAdapter.isChatting(receiverChat.getUserId().value(), receiverChat.getId().value());

        ImPrivateInboxMessage senderInboxMessage = ImPrivateInboxMessage.builder()
                .id(messageId)
                .token(messageToken)
                .content(messageContent)
                .chatId(senderChat.getId())
                .userId(senderChat.getUserId())
                .senderId(userId)
                .status(ImPrivateMessageStatus.SENT)
                .sendTime(LocalDateTime.now())
                .receivedTime(LocalDateTime.now())
                .readTime(LocalDateTime.now())
                .build();
        ImPrivateInboxMessage receiverInboxMessage = ImPrivateInboxMessage.builder()
                .id(messageId)
                .token(messageToken)
                .content(messageContent)
                .chatId(receiverChat.getId())
                .userId(receiverChat.getUserId())
                .senderId(userId)
                .status(ImPrivateMessageStatus.SENT)
                .sendTime(LocalDateTime.now())
                .build();

        transactionOperations.executeWithoutResult(status -> {
            imPrivateInboxMessageRepository.save(senderInboxMessage);
            imPrivateInboxMessageRepository.save(receiverInboxMessage);

            senderChat.receiveLatestMessage(senderInboxMessage, true);
            imPrivateChatRepository.save(senderChat);

            receiverChat.receiveLatestMessage(
                    receiverInboxMessage,
                    receiverChatting
            );
            imPrivateChatRepository.save(receiverChat);

            ImPrivateMessageSentEvent imMessageSentEvent = imMessageService.newImMessageSentEvent(receiverInboxMessage);
            imMessageEventPublisher.publish(imMessageSentEvent);
        });
    }

    @AfterTransactionCommit
    public void onMessageSent(ImPrivateMessageSentEvent event) {
        imMessageNotifierInvoker.invoke(ImMessageAppTransformer.INSTANCE.imPrivateSentNotificationFrom(event));
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void receiveMessage(ImPrivateMessageReceiveCmd command) {
        UserId userId = new UserId(command.userId());
        ImChatId chatId = new ImChatId(command.chatId());
        ImMessageId messageId = new ImMessageId(command.messageId());

        ImPrivateChat privateChat = imPrivateChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(privateChat, userId);

        ImPrivateInboxMessage imMessage = imPrivateInboxMessageRepository.find(chatId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        imMessage.receive(userId);
        imPrivateInboxMessageRepository.save(imMessage);
    }

    @DistributeLock(scene = ImMessageLockScene.PRIVATE_MESSAGE_REVOKE, key = "#command.chatId() + ':' + #command.messageId()")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void revokeMessage(ImPrivateMessageRevokeCmd command) {
        UserId userId = new UserId(command.userId());
        ImChatId chatId = new ImChatId(command.chatId());
        ImMessageId messageId = new ImMessageId(command.messageId());

        ImPrivateChat senderChat = imPrivateChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(senderChat, userId);

        ImPrivateChat receiverChat = imChatService.getPeerChat(senderChat).orElseThrow(() -> new NotFoundException("接收方会话不存在"));
        imChatService.ensureBelongsTo(receiverChat, senderChat.getPeerUserId());

        ImPrivateInboxMessage senderInboxMessage = imPrivateInboxMessageRepository
                .find(senderChat.getId(), messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        ImPrivateInboxMessage receiverInboxMessage = imPrivateInboxMessageRepository
                .find(receiverChat.getId(), messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        ImPrivateMessageRevocation revocation =
                imMessageService.revokePrivateMessage(senderInboxMessage, receiverInboxMessage, senderChat.getUserId());
        imPrivateInboxMessageRepository.saveBatch(revocation.getMessages());

        ImPrivateMessageRevokedEvent imMessageRevokedEvent =
                imMessageService.newImMessageRevokedEvent(revocation.senderMessage(), receiverChat.getUserId());
        imMessageEventPublisher.publish(imMessageRevokedEvent);
    }

    @AfterTransactionCommit
    public void onMessageRevoked(ImPrivateMessageRevokedEvent event) {
        imMessageNotifierInvoker.invoke(ImMessageAppTransformer.INSTANCE.imPrivateRevokedNotificationFrom(event));
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void readMessage(ImPrivateMessageReadCmd command) {
        UserId userId = new UserId(command.userId());
        ImChatId chatId = new ImChatId(command.chatId());
        ImMessageId messageId = new ImMessageId(command.messageId());

        ImPrivateChat imPrivateChat = imPrivateChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(imPrivateChat, userId);

        ImPrivateInboxMessage imMessage = imPrivateInboxMessageRepository.find(chatId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        imMessage.read(userId);
        imPrivateInboxMessageRepository.save(imMessage);

        imPrivateChat.readMessage(imMessage);
        imPrivateChatRepository.save(imPrivateChat);
    }

    public List<ImPrivateMessageDTO> queryHistoryMessage(ImPrivateMessageHistoryQuery query) {
        UserId userId = new UserId(query.userId());
        ImChatId imChatId = new ImChatId(query.chatId());
        ImMessageId imLastMessageId = FunctionUtils.mappingOrNull(query.lastMessageId(), ImMessageId::new);
        Integer count = query.count();

        ImPrivateChat imPrivateChat = imPrivateChatRepository.find(imChatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(imPrivateChat, userId);

        List<ImPrivateInboxMessage> messageList = imPrivateInboxMessageRepository.queryHistory(imChatId, imLastMessageId, count, userId);
        return ImMessageAppTransformer.INSTANCE.imPrivateMessageDtoListFrom(messageList);
    }

    public ImPrivateMessageDTO queryMessageDetail(ImPrivateMessageDetailQuery query) {
        UserId userId = new UserId(query.userId());
        ImChatId chatId = new ImChatId(query.chatId());
        ImMessageToken messageToken = new ImMessageToken(query.messageToken());

        ImPrivateChat imPrivateChat = imPrivateChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(imPrivateChat, userId);

        ImPrivateInboxMessage imPrivateMessage = imPrivateInboxMessageRepository
                .queryDetail(chatId, messageToken, userId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        return ImMessageAppTransformer.INSTANCE.imPrivateMessageDtoFrom(imPrivateMessage);
    }

    private static TransactionOperations immediateTransactionOperations() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }

            @Override
            public void executeWithoutResult(java.util.function.Consumer<TransactionStatus> action) {
                action.accept(null);
            }
        };
    }
}
