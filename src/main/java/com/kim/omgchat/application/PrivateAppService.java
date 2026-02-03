package com.kim.omgchat.application;

import com.kim.omgchat.common.exception.NotFoundException;
import com.kim.omgchat.common.identity.snowflake.SnowflakeId;
import com.kim.omgchat.domain.chat.ImChat;
import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.chat.ImChatRepository;
import com.kim.omgchat.domain.chat.ImChatService;
import com.kim.omgchat.domain.message.ImMessageStatus;
import com.kim.omgchat.domain.message.ImPrivateMessage;
import com.kim.omgchat.domain.message.ImMessageContent;
import com.kim.omgchat.domain.message.ImMessageId;
import com.kim.omgchat.domain.message.ImPrivateMessageRepository;
import com.kim.omgchat.domain.message.ImMessageToken;
import com.kim.omgchat.domain.user.UserId;
import com.kim.omgchat.domain.user.UserService;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageNotifyDTO;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageReadNotifyDTO;
import com.kim.omgchat.model.cqrs.dto.im.ImPrivateMessageRevokeNotifyDTO;
import com.kim.omgchat.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.kim.omgchat.support.ImMessageNotifier;
import com.kim.omgchat.transformer.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class PrivateAppService {

    private final SnowflakeId snowflakeId;
    private final ImChatRepository imChatRepository;
    private final ImPrivateMessageRepository imMessageRepository;

    private final UserService userService;
    private final ImChatService imChatService;

    private final ImMessageNotifier imMessageNotifier;

    public void sendMessage(ImPrivateMessageSendCmd command) {
        ImMessageId messageId = new ImMessageId(snowflakeId.next());
        ImChatId chatId = new ImChatId(command.getChatId());
        UserId senderId = new UserId(command.getSenderId());
        UserId receiverId = new UserId(command.getReceiverId());
        ImMessageToken messageToken = new ImMessageToken(command.getMessageToken());
        ImMessageContent messageContent = new ImMessageContent(command.getMessageType(), command.getMessageContent());

        ImChat imChat = imChatRepository.find(chatId);
        if (imChat == null) {
            throw new NotFoundException("chat is not exist");
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
        imMessageRepository.save(imMessage);

        boolean isChatting = userService.isChatting(chatId, receiverId);
        if (isChatting) {
            ImPrivateMessageNotifyDTO notifyDTO =
                    ImMessageAppTransformer.INSTANCE.imPrivateMessageNotifyDtoFrom(imMessage);
            imMessageNotifier.notify(notifyDTO);
        } else {
            imChatService.increaseUnreadCount(chatId, receiverId);
        }
    }

    public void revokeMessage(ImPrivateMessageRevokeCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImPrivateMessage imMessage = imMessageRepository.find(chatId, messageId);
        if (imMessage == null) {
            throw new NotFoundException("消息不存在");
        }

        imMessage.revoke(userId);
        imMessageRepository.save(imMessage);

        ImPrivateMessageRevokeNotifyDTO notifyDTO =
                ImMessageAppTransformer.INSTANCE.imPrivateMessageRevokeNotifyDtoFrom(imMessage);
        imMessageNotifier.notify(notifyDTO);
    }

    public void readMessage(ImPrivateMessageReadCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImPrivateMessage imMessage = imMessageRepository.find(chatId, messageId);
        if (imMessage == null) {
            throw new NotFoundException("消息不存在");
        }

        imMessage.read(userId);
        imMessageRepository.save(imMessage);

        // 更新用户未读数量
        imChatService.decreaseUnreadCount(chatId, userId, messageId);

        ImPrivateMessageReadNotifyDTO notifyDTO =
                ImMessageAppTransformer.INSTANCE.imPrivateMessageReadNotifyDtoFrom(imMessage);
        imMessageNotifier.notify(notifyDTO);
    }

    public void queryHistoryMessage(ImPrivateMessageHistoryQuery query) {
    }
}
