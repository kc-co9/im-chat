package com.co.kc.imchat.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.domain.chat.ImChat;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatRepository;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.message.ImGroupMessage;
import com.co.kc.imchat.domain.message.ImGroupMessageRepository;
import com.co.kc.imchat.domain.message.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImGroupMessageSentEvent;
import com.co.kc.imchat.domain.message.ImMessageContent;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.message.ImMessageStatus;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.command.im.ImGroupMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImGroupMessageSendCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImGroupRevokedNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImGroupSentNotifyCmd;
import com.co.kc.imchat.support.ImMessageNotifier;
import com.co.kc.imchat.support.event.DomainEventPublisher;
import com.co.kc.imchat.transformer.ImMessageAppTransformer;
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
        ImChatId chatId = new ImChatId(event.getChatId());
        ImGroupChat imGroupChat = imChatRepository.findGroupChat(chatId);
        for (ImGroupMember member : imGroupChat.getMembers()) {
            ImGroupSentNotifyCmd notifyCmd =
                    ImMessageAppTransformer.INSTANCE.imGroupSentNotifyCmdFrom(member.getUserId().getValue(), event);
            imMessageNotifier.notify(notifyCmd);
        }
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
        ImChatId chatId = new ImChatId(event.getChatId());
        ImGroupChat imGroupChat = imChatRepository.findGroupChat(chatId);
        for (ImGroupMember member : imGroupChat.getMembers()) {
            ImGroupRevokedNotifyCmd notifyCmd =
                    ImMessageAppTransformer.INSTANCE.imGroupRevokedNotifyCmdFrom(member.getUserId().getValue(), event);
            imMessageNotifier.notify(notifyCmd);
        }

    }

}
