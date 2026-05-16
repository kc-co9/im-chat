package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.message.ImPrivateMessageStatus;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.model.cqrs.query.ImPrivateMessageDetailQuery;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.exception.RepeatException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.message.ImPrivateMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageSentEvent;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.message.ImMessageContent;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserService;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageReceiveCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.domain.message.ImPrivateMessageReceivedEvent;
import com.co.kc.imchat.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.co.kc.imchat.support.event.DomainEventPublisher;
import com.co.kc.imchat.support.lock.DistributeLockScene;
import com.co.kc.imchat.support.lock.annotation.DistributeLock;
import com.co.kc.imchat.support.notifier.ImMessageNotifierInvoker;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.application.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class PrivateMessageAppService {

    private final SnowflakeId snowflakeId;
    private final ImPrivateChatRepository imPrivateChatRepository;
    private final ImPrivateInboxMessageRepository imPrivateInboxMessageRepository;

    private final UserService userService;
    private final ImChatService imChatService;
    private final ImMessageService imMessageService;

    private final ImMessageNotifierInvoker imMessageNotifierInvoker;
    private final DomainEventPublisher imMessageEventPublisher;

    @DistributeLock(scene = DistributeLockScene.PRIVATE_MESSAGE_SEND, key = "#command.chatId + ':' + #command.messageToken")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void sendMessage(ImPrivateMessageSendCmd command) {
        ImMessageId messageId = new ImMessageId(snowflakeId.next());
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageToken messageToken = new ImMessageToken(command.getMessageToken());
        ImMessageContent messageContent = new ImMessageContent(command.getMessageType(), command.getMessageContent());

        ImPrivateChat senderChat = imPrivateChatRepository.find(chatId)
                .orElseThrow(() -> new NotFoundException("聊天不存在"));
        if (!senderChat.belongsTo(userId)) {
            throw new BusinessException("请使用本人私聊会话的 chatId 发送消息");
        }

        ImPrivateChat receiverChat = imChatService.getPeerChat(senderChat)
                .orElseThrow(() -> new NotFoundException("接收方会话不存在"));
        if (!receiverChat.belongsTo(senderChat.getPeerUserId())) {
            throw new BusinessException("请使用本人私聊会话的 chatId 获取消息");
        }

        boolean hasContained = imPrivateInboxMessageRepository.contain(chatId, messageToken);
        if (hasContained) {
            throw new RepeatException("消息已存在");
        }

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
        imPrivateInboxMessageRepository.save(senderInboxMessage);
        imPrivateInboxMessageRepository.save(receiverInboxMessage);

        senderChat.receiveLatestMessage(senderInboxMessage, true);
        imPrivateChatRepository.save(senderChat);

        receiverChat.receiveLatestMessage(receiverInboxMessage, userService.isChatting(receiverChat));
        imPrivateChatRepository.save(receiverChat);

        ImPrivateMessageSentEvent imMessageSentEvent = imMessageService.newImMessageSentEvent(receiverInboxMessage);
        imMessageEventPublisher.publish(imMessageSentEvent);
    }

    public void onMessageSent(ImPrivateMessageSentEvent event) {
        UserId receiverId = new UserId(event.getReceiverId());
        boolean isOnline = userService.isOnline(receiverId);
        if (!isOnline) {
            return;
        }
        imMessageNotifierInvoker.invoke(ImMessageAppTransformer.INSTANCE.imPrivateSentNotifyCmdFrom(event));
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void receiveMessage(ImPrivateMessageReceiveCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImPrivateChat receiverChat = imPrivateChatRepository.find(chatId)
                .orElseThrow(() -> new NotFoundException("聊天不存在"));
        if (!receiverChat.belongsTo(userId)) {
            throw new BusinessException("请使用本人私聊会话的 chatId");
        }

        ImPrivateInboxMessage imMessage = imPrivateInboxMessageRepository.find(chatId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        imMessage.receive(userId);
        imPrivateInboxMessageRepository.save(imMessage);

        ImPrivateMessageReceivedEvent imMessageReceivedEvent = imMessageService.newImMessageReceivedEvent(imMessage);
        imMessageEventPublisher.publish(imMessageReceivedEvent);
    }

    public void onMessageReceived(ImPrivateMessageReceivedEvent event) {
    }


    @DistributeLock(scene = DistributeLockScene.PRIVATE_MESSAGE_REVOKE, key = "#command.chatId + ':' + #command.messageId")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void revokeMessage(ImPrivateMessageRevokeCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImPrivateChat senderChat = imPrivateChatRepository.find(chatId)
                .orElseThrow(() -> new NotFoundException("聊天不存在"));
        if (!senderChat.belongsTo(userId)) {
            throw new BusinessException("请使用本人私聊会话的 chatId");
        }

        ImPrivateChat receiverChat = imChatService.getPeerChat(senderChat)
                .orElseThrow(() -> new NotFoundException("接收方会话不存在"));
        if (!receiverChat.belongsTo(senderChat.getPeerUserId())) {
            throw new BusinessException("请使用本人私聊会话的 chatId 获取消息");
        }

        ImPrivateInboxMessage senderInboxMessage = imPrivateInboxMessageRepository
                .find(senderChat.getId(), messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        ImPrivateInboxMessage receiverInboxMessage = imPrivateInboxMessageRepository
                .find(receiverChat.getId(), messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        senderInboxMessage.revoke(senderChat.getUserId());
        receiverInboxMessage.revoke(senderChat.getUserId());
        imPrivateInboxMessageRepository.save(senderInboxMessage);
        imPrivateInboxMessageRepository.save(receiverInboxMessage);

        ImPrivateMessageRevokedEvent imMessageRevokedEvent =
                imMessageService.newImMessageRevokedEvent(senderInboxMessage, receiverChat.getUserId());
        imMessageEventPublisher.publish(imMessageRevokedEvent);
    }

    public void onMessageRevoked(ImPrivateMessageRevokedEvent event) {
        imMessageNotifierInvoker.invoke(ImMessageAppTransformer.INSTANCE.imPrivateRevokedNotifyCmdFrom(event));
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void readMessage(ImPrivateMessageReadCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImPrivateChat imPrivateChat = imPrivateChatRepository.find(chatId)
                .orElseThrow(() -> new NotFoundException("聊天不存在"));
        if (!imPrivateChat.belongsTo(userId)) {
            throw new BusinessException("请使用本人私聊会话的 chatId");
        }

        ImPrivateInboxMessage imMessage = imPrivateInboxMessageRepository.find(chatId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        imMessage.read(userId);
        imPrivateInboxMessageRepository.save(imMessage);

        imPrivateChat.readMessage(imMessage);
        imPrivateChatRepository.save(imPrivateChat);
    }

    public List<ImPrivateMessageDTO> queryHistoryMessage(ImPrivateMessageHistoryQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId imChatId = new ImChatId(query.getChatId());
        ImMessageId imLastMessageId = FunctionUtils.mappingOrNull(query.getLastMessageId(), ImMessageId::new);
        Integer count = query.getCount();

        ImPrivateChat imPrivateChat = imPrivateChatRepository.find(imChatId)
                .orElseThrow(() -> new NotFoundException("聊天不存在"));
        if (!imPrivateChat.belongsTo(userId)) {
            throw new BusinessException("请使用本人私聊会话的 chatId 查询记录");
        }

        List<ImPrivateInboxMessage> messageList = imPrivateInboxMessageRepository.queryHistory(imChatId, imLastMessageId, count, userId);
        return ImMessageAppTransformer.INSTANCE.imPrivateMessageDtoListFrom(messageList);
    }

    public ImPrivateMessageDTO queryMessageDetail(ImPrivateMessageDetailQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId chatId = new ImChatId(query.getChatId());
        ImMessageToken messageToken = new ImMessageToken(query.getMessageToken());

        ImPrivateChat imPrivateChat = imPrivateChatRepository.find(chatId)
                .orElseThrow(() -> new NotFoundException("聊天不存在"));
        if (!imPrivateChat.belongsTo(userId)) {
            throw new BusinessException("请使用本人私聊会话的 chatId 查询消息");
        }

        ImPrivateInboxMessage imPrivateMessage = imPrivateInboxMessageRepository
                .queryDetail(chatId, messageToken, userId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        return ImMessageAppTransformer.INSTANCE.imPrivateMessageDtoFrom(imPrivateMessage);
    }
}
