package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.message.ImPrivateMessageStatus;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.model.cqrs.query.ImPrivateMessageDetailQuery;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatRepository;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.message.ImPrivateMessageReadEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageSentEvent;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.message.ImPrivateMessage;
import com.co.kc.imchat.domain.message.ImMessageContent;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImPrivateMessageRepository;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserService;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageReceiveCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateRevokedNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
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
        ImMessageToken messageToken = new ImMessageToken(command.getMessageToken());
        ImMessageContent messageContent = new ImMessageContent(command.getMessageType(), command.getMessageContent());

        ImPrivateChat imPrivateChat = imChatRepository.findPrivateChat(chatId);
        if (imPrivateChat == null) {
            throw new NotFoundException("聊天不存在");
        }

        ImPrivateMessage imMessage = new ImPrivateMessage();
        imMessage.setId(messageId);
        imMessage.setToken(messageToken);
        imMessage.setContent(messageContent);
        imMessage.setChatId(chatId);
        imMessage.setSenderId(senderId);
        imMessage.setReceiverId(imPrivateChat.getAnother(senderId));
        imMessage.setStatus(ImPrivateMessageStatus.SENT);
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

    public List<ImPrivateMessageDTO> queryHistoryMessage(ImPrivateMessageHistoryQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId imChatId = new ImChatId(query.getChatId());
        ImMessageId imLastMessageId = FunctionUtils.mappingOrNull(query.getLastMessageId(), ImMessageId::new);
        Integer count = query.getCount();

        ImPrivateChat imPrivateChat = imChatRepository.findPrivateChat(imChatId);
        if (imPrivateChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!imPrivateChat.contain(userId)) {
            throw new BusinessException("无法查看别人的聊天记录");
        }

        List<ImPrivateMessage> messageList = imPrivateMessageRepository.queryHistory(imChatId, imLastMessageId, count);
        return ImMessageAppTransformer.INSTANCE.imPrivateMessageDtoListFrom(messageList);
    }

    public ImPrivateMessageDTO queryMessageDetail(ImPrivateMessageDetailQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId chatId = new ImChatId(query.getChatId());
        ImMessageToken messageToken = new ImMessageToken(query.getMessageToken());

        ImPrivateChat imPrivateChat = imChatRepository.findPrivateChat(chatId);
        if (imPrivateChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!imPrivateChat.contain(userId)) {
            throw new BusinessException("无法查看别人的聊天记录");
        }

        ImPrivateMessage imPrivateMessage = imPrivateMessageRepository.queryDetail(chatId, messageToken);
        return ImMessageAppTransformer.INSTANCE.imPrivateMessageDtoFrom(imPrivateMessage);
    }
}
