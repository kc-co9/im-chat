package com.kim.omgchat.application;

import com.kim.omgchat.common.exception.NotFoundException;
import com.kim.omgchat.common.identity.snowflake.SnowflakeId;
import com.kim.omgchat.domain.chat.ImChat;
import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.chat.ImChatRepository;
import com.kim.omgchat.domain.chat.ImChatService;
import com.kim.omgchat.domain.message.ImPrivateMessageReadEvent;
import com.kim.omgchat.domain.message.ImPrivateMessageRevokedEvent;
import com.kim.omgchat.domain.message.ImPrivateMessageSentEvent;
import com.kim.omgchat.domain.message.ImMessageService;
import com.kim.omgchat.domain.message.ImMessageStatus;
import com.kim.omgchat.domain.message.ImPrivateMessage;
import com.kim.omgchat.domain.message.ImMessageContent;
import com.kim.omgchat.domain.message.ImMessageId;
import com.kim.omgchat.domain.message.ImPrivateMessageRepository;
import com.kim.omgchat.domain.message.ImMessageToken;
import com.kim.omgchat.domain.user.UserId;
import com.kim.omgchat.domain.user.UserService;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageReceiveCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateRevokedNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateReadNotifyCmd;
import com.kim.omgchat.model.cqrs.dto.im.ImMessageDTO;
import com.kim.omgchat.domain.message.ImPrivateMessageReceivedEvent;
import com.kim.omgchat.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.kim.omgchat.support.event.DomainEventPublisher;
import com.kim.omgchat.support.ImMessageNotifier;
import com.kim.omgchat.transformer.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class ImPrivateAppService {

    private final SnowflakeId snowflakeId;
    private final ImChatRepository imChatRepository;
    private final ImPrivateMessageRepository imPrivateMessageRepository;

    private final UserService userService;
    private final ImChatService imChatService;
    private final ImMessageService imMessageService;

    private final ImMessageNotifier imMessageNotifier;
    private final DomainEventPublisher imMessageEventPublisher;

    public void sendMessage(ImPrivateMessageSendCmd command) {
        ImMessageId messageId = new ImMessageId(snowflakeId.next());
        ImChatId chatId = new ImChatId(command.getChatId());
        UserId senderId = new UserId(command.getSenderId());
        UserId receiverId = new UserId(command.getReceiverId());
        ImMessageToken messageToken = new ImMessageToken(command.getMessageToken());
        ImMessageContent messageContent = new ImMessageContent(command.getMessageType(), command.getMessageContent());

        ImChat imChat = imChatRepository.find(chatId);
        if (imChat == null) {
            throw new NotFoundException("聊天不存在");
        }

        ImPrivateMessage imMessage = new ImPrivateMessage();
        imMessage.setId(messageId);
        imMessage.setToken(messageToken);
        imMessage.setContent(messageContent);
        imMessage.setChatId(chatId);
        imMessage.setSenderId(senderId);
        imMessage.setReceiverId(receiverId);
        imMessage.setStatus(ImMessageStatus.SENT);
        imMessage.setSendTime(LocalDateTime.now());
        imMessage.validate();
        imPrivateMessageRepository.save(imMessage);

        ImPrivateMessageSentEvent imMessageSentEvent = imMessageService.newImMessageSentEvent(imMessage);
        imMessageEventPublisher.publish(imMessageSentEvent);
    }

    public void onMessageSent(ImPrivateMessageSentEvent event) {
        ImChatId chatId = new ImChatId(event.getChatId());
        UserId receiverId = new UserId(event.getReceiverId());

        boolean isChatting = userService.isChatting(chatId, receiverId);
        if (isChatting) {
            ImPrivateSentNotifyCmd notifyCmd = ImMessageAppTransformer.INSTANCE.imPrivateSentNotifyCmdFrom(event);
            imMessageNotifier.notify(notifyCmd);
        } else {
            imChatService.increaseUnreadCount(chatId, receiverId);
        }
    }

    public void receiveMessage(ImPrivateMessageReceiveCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImPrivateMessage imMessage = imPrivateMessageRepository.find(chatId, messageId);
        if (imMessage == null) {
            throw new NotFoundException("消息不存在");
        }

        imMessage.receive(userId);
        imPrivateMessageRepository.save(imMessage);

        ImPrivateMessageReceivedEvent imMessageReceivedEvent = imMessageService.newImMessageReceivedEvent(imMessage);
        imMessageEventPublisher.publish(imMessageReceivedEvent);
    }

    public void onMessageReceived(ImPrivateMessageReceivedEvent event) {
    }


    public void revokeMessage(ImPrivateMessageRevokeCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImPrivateMessage imMessage = imPrivateMessageRepository.find(chatId, messageId);
        if (imMessage == null) {
            throw new NotFoundException("消息不存在");
        }

        imMessage.revoke(userId);
        imPrivateMessageRepository.save(imMessage);

        ImPrivateMessageRevokedEvent imMessageRevokedEvent = imMessageService.newImMessageRevokedEvent(imMessage);
        imMessageEventPublisher.publish(imMessageRevokedEvent);
    }

    public void onMessageRevoked(ImPrivateMessageRevokedEvent event) {
        ImPrivateRevokedNotifyCmd notifyCmd = ImMessageAppTransformer.INSTANCE.imPrivateRevokedNotifyCmdFrom(event);
        imMessageNotifier.notify(notifyCmd);
    }

    public void readMessage(ImPrivateMessageReadCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImPrivateMessage imMessage = imPrivateMessageRepository.find(chatId, messageId);
        if (imMessage == null) {
            throw new NotFoundException("消息不存在");
        }

        imMessage.read(userId);
        imPrivateMessageRepository.save(imMessage);

        ImPrivateMessageReadEvent imMessageReadEvent = imMessageService.newImMessageReadEvent(imMessage);
        imMessageEventPublisher.publish(imMessageReadEvent);

        // 更新用户未读数量
        imChatService.decreaseUnreadCount(chatId, userId, messageId);

        ImPrivateReadNotifyCmd notifyDTO =
                ImMessageAppTransformer.INSTANCE.imPrivateMessageReadNotifyDtoFrom(imMessage);
        imMessageNotifier.notify(notifyDTO);
    }

    public void onMessageRead(ImPrivateMessageReadEvent event) {
        ImPrivateReadNotifyCmd notifyCmd = ImMessageAppTransformer.INSTANCE.imPrivateMessageReadNotifyCmdFrom(event);
        imMessageNotifier.notify(notifyCmd);
    }

    public List<ImMessageDTO> queryHistoryMessage(ImPrivateMessageHistoryQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId chatId = new ImChatId(query.getChatId());
        ImMessageId lastMessageId = new ImMessageId(query.getLastMessageId());
        Integer count = query.getCount();
        List<ImPrivateMessage> messageList = imPrivateMessageRepository.queryHistory(chatId, userId, lastMessageId, count);
        return ImMessageAppTransformer.INSTANCE.imMessageDtoListFrom(messageList);
    }
}
