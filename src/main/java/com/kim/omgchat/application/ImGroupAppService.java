package com.kim.omgchat.application;

import com.kim.omgchat.common.exception.NotFoundException;
import com.kim.omgchat.common.identity.snowflake.SnowflakeId;
import com.kim.omgchat.domain.chat.ImChat;
import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.chat.ImChatRepository;
import com.kim.omgchat.domain.message.ImGroupMessage;
import com.kim.omgchat.domain.message.ImGroupMessageRepository;
import com.kim.omgchat.domain.message.ImGroupMessageRevokedEvent;
import com.kim.omgchat.domain.message.ImGroupMessageSentEvent;
import com.kim.omgchat.domain.message.ImMessageContent;
import com.kim.omgchat.domain.message.ImMessageId;
import com.kim.omgchat.domain.message.ImMessageService;
import com.kim.omgchat.domain.message.ImMessageStatus;
import com.kim.omgchat.domain.message.ImMessageToken;
import com.kim.omgchat.domain.user.UserId;
import com.kim.omgchat.model.cqrs.command.im.ImGroupMessageRevokeCmd;
import com.kim.omgchat.model.cqrs.command.im.ImGroupMessageSendCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImGroupRevokedNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImGroupSentNotifyCmd;
import com.kim.omgchat.support.ImMessageNotifier;
import com.kim.omgchat.support.event.DomainEventPublisher;
import com.kim.omgchat.transformer.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群组-应用服务
 */
@RequiredArgsConstructor
public class ImGroupAppService {
    private final SnowflakeId snowflakeId;
    private final ImChatRepository imChatRepository;
    private final ImGroupMessageRepository imGroupMessageRepository;

    private final ImMessageService imMessageService;

    private final ImMessageNotifier imMessageNotifier;
    private final DomainEventPublisher imMessageEventPublisher;

    public void sendMessage(ImGroupMessageSendCmd command) {
        ImMessageId messageId = new ImMessageId(snowflakeId.next());
        ImChatId chatId = new ImChatId(command.getChatId());
        UserId senderId = new UserId(command.getSenderId());
        ImMessageToken messageToken = new ImMessageToken(command.getMessageToken());
        ImMessageContent messageContent = new ImMessageContent(command.getMessageType(), command.getMessageContent());

        ImChat imChat = imChatRepository.find(chatId);
        if (imChat == null) {
            throw new NotFoundException("聊天不存在");
        }

        ImGroupMessage imMessage = new ImGroupMessage();
        imMessage.setId(messageId);
        imMessage.setToken(messageToken);
        imMessage.setContent(messageContent);
        imMessage.setChatId(chatId);
        imMessage.setSenderId(senderId);
        imMessage.setStatus(ImMessageStatus.SENT);
        imMessage.setSendTime(LocalDateTime.now());
        imMessage.validate();
        imGroupMessageRepository.save(imMessage);

        ImGroupMessageSentEvent imMessageSentEvent = imMessageService.newImMessageSentEvent(imMessage);
        imMessageEventPublisher.publish(imMessageSentEvent);
    }

    public void onMessageSent(ImGroupMessageSentEvent event) {
        ImGroupSentNotifyCmd notifyCmd = ImMessageAppTransformer.INSTANCE.imGroupSentNotifyCmdFrom(event);
        imMessageNotifier.notify(notifyCmd);
    }

    public void revokeMessage(ImGroupMessageRevokeCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImGroupMessage imMessage = imGroupMessageRepository.find(chatId, messageId);
        if (imMessage == null) {
            throw new NotFoundException("消息不存在");
        }

        imMessage.revoke(userId);
        imGroupMessageRepository.save(imMessage);

        ImGroupMessageRevokedEvent imMessageRevokedEvent = imMessageService.newImMessageRevokedEvent(imMessage);
        imMessageEventPublisher.publish(imMessageRevokedEvent);
    }

    public void onMessageRevoked(ImGroupMessageRevokedEvent event) {
        ImGroupRevokedNotifyCmd notifyCmd = ImMessageAppTransformer.INSTANCE.imGroupRevokedNotifyCmdFrom(event);
        imMessageNotifier.notify(notifyCmd);
    }

}
