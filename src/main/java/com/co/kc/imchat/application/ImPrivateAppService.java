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
import com.co.kc.imchat.domain.message.ImPrivateMessageReadEvent;
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
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateReadNotifyCmd;
import com.co.kc.imchat.domain.message.ImPrivateMessageReceivedEvent;
import com.co.kc.imchat.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.co.kc.imchat.support.event.DomainEventPublisher;
import com.co.kc.imchat.support.ImMessageNotifier;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.application.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class ImPrivateAppService {

    private final SnowflakeId snowflakeId;
    private final ImPrivateChatRepository imPrivateChatRepository;
    private final ImPrivateInboxMessageRepository imPrivateInboxMessageRepository;

    private final UserService userService;
    private final ImChatService imChatService;
    private final ImMessageService imMessageService;

    private final ImMessageNotifier imMessageNotifier;
    private final DomainEventPublisher imMessageEventPublisher;

    public void sendMessage(ImPrivateMessageSendCmd command) {
        ImMessageId messageId = new ImMessageId(snowflakeId.next());
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageToken messageToken = new ImMessageToken(command.getMessageToken());
        ImMessageContent messageContent = new ImMessageContent(command.getMessageType(), command.getMessageContent());

        ImPrivateChat senderChat = imPrivateChatRepository.find(chatId);
        if (senderChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!senderChat.getUserId().equals(userId)) {
            throw new BusinessException("请使用本人私聊会话的 chatId 发送消息");
        }

        ImPrivateChat receiverChat = imChatService.getPeerChat(senderChat);
        if (receiverChat == null) {
            throw new NotFoundException("接收方会话不存在");
        }
        if (!receiverChat.getUserId().equals(senderChat.getPeerUserId())) {
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

        // TODO 添加消息通知重试机制
    }

    public void onMessageSent(ImPrivateMessageSentEvent event) {
        UserId receiverId = new UserId(event.getReceiverId());
        ImChatId receiverChatId = new ImChatId(event.getReceiverChatId());

        boolean isChatting = userService.isChatting(receiverChatId, receiverId);
        if (isChatting) {
            imMessageNotifier.notify(ImMessageAppTransformer.INSTANCE.imPrivateSentNotifyCmdFrom(event));
        }
    }

    public void receiveMessage(ImPrivateMessageReceiveCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImPrivateChat receiverChat = imPrivateChatRepository.find(chatId);
        if (receiverChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!receiverChat.getUserId().equals(userId)) {
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
        // TODO 清理重试发送
    }


    public void revokeMessage(ImPrivateMessageRevokeCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImPrivateChat senderChat = imPrivateChatRepository.find(chatId);
        if (senderChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!senderChat.getUserId().equals(userId)) {
            throw new BusinessException("请使用本人私聊会话的 chatId");
        }

        ImPrivateChat receiverChat = imChatService.getPeerChat(senderChat);
        if (receiverChat == null) {
            throw new NotFoundException("接收方会话不存在");
        }
        if (!receiverChat.getUserId().equals(senderChat.getPeerUserId())) {
            throw new BusinessException("请使用本人私聊会话的 chatId 获取消息");
        }

        ImPrivateInboxMessage senderInboxMessage = imPrivateInboxMessageRepository
                .find(senderChat.getId(), messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        ImPrivateInboxMessage receiverInboxMessage = imPrivateInboxMessageRepository
                .find(receiverChat.getId(), messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        senderInboxMessage.revoke(senderChat.getUserId());
        receiverInboxMessage.revoke(receiverChat.getUserId());
        imPrivateInboxMessageRepository.save(senderInboxMessage);
        imPrivateInboxMessageRepository.save(receiverInboxMessage);

        ImPrivateMessageRevokedEvent imMessageRevokedEvent =
                imMessageService.newImMessageRevokedEvent(senderInboxMessage, receiverChat.getUserId());
        imMessageEventPublisher.publish(imMessageRevokedEvent);

        // TODO 添加消息通知重试机制
    }

    public void onMessageRevoked(ImPrivateMessageRevokedEvent event) {
        imMessageNotifier.notify(ImMessageAppTransformer.INSTANCE.imPrivateRevokedNotifyCmdFrom(event));
    }

    public void readMessage(ImPrivateMessageReadCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImPrivateChat imPrivateChat = imPrivateChatRepository.find(chatId);
        if (imPrivateChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!imPrivateChat.getUserId().equals(userId)) {
            throw new BusinessException("请使用本人私聊会话的 chatId");
        }

        ImPrivateInboxMessage imMessage = imPrivateInboxMessageRepository.find(chatId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        imMessage.read(userId);
        imPrivateInboxMessageRepository.save(imMessage);

        imPrivateChat.readMessage(imMessage);
        imPrivateChatRepository.save(imPrivateChat);

        ImPrivateMessageReadEvent imMessageReadEvent = imMessageService.newImMessageReadEvent(imMessage);
        imMessageEventPublisher.publish(imMessageReadEvent);
    }

    public void onMessageRead(ImPrivateMessageReadEvent event) {
        ImPrivateReadNotifyCmd notifyCmd = ImMessageAppTransformer.INSTANCE.imPrivateMessageReadNotifyCmdFrom(event);
        imMessageNotifier.notify(notifyCmd);
    }

    public List<ImPrivateMessageDTO> queryHistoryMessage(ImPrivateMessageHistoryQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId imChatId = new ImChatId(query.getChatId());
        ImMessageId imLastMessageId = FunctionUtils.mappingOrNull(query.getLastMessageId(), ImMessageId::new);
        Integer count = query.getCount();

        ImPrivateChat imPrivateChat = imPrivateChatRepository.find(imChatId);
        if (imPrivateChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!imPrivateChat.getUserId().equals(userId)) {
            throw new BusinessException("请使用本人私聊会话的 chatId 查询记录");
        }

        List<ImPrivateInboxMessage> messageList = imPrivateInboxMessageRepository.queryHistory(imChatId, imLastMessageId, count, userId);
        return ImMessageAppTransformer.INSTANCE.imPrivateMessageDtoListFrom(messageList);
    }

    public ImPrivateMessageDTO queryMessageDetail(ImPrivateMessageDetailQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId chatId = new ImChatId(query.getChatId());
        ImMessageToken messageToken = new ImMessageToken(query.getMessageToken());

        ImPrivateChat imPrivateChat = imPrivateChatRepository.find(chatId);
        if (imPrivateChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!imPrivateChat.getUserId().equals(userId)) {
            throw new BusinessException("请使用本人私聊会话的 chatId 查询消息");
        }

        ImPrivateInboxMessage imPrivateMessage = imPrivateInboxMessageRepository
                .queryDetail(chatId, messageToken, userId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        return ImMessageAppTransformer.INSTANCE.imPrivateMessageDtoFrom(imPrivateMessage);
    }
}
