package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import com.co.kc.imchat.transformer.application.ImMessageAppTransformer;

import java.time.LocalDateTime;

/**
 * IM消息-领域服务
 */
public class ImMessageService {
    public ImPrivateMessageSentEvent newImMessageSentEvent(ImPrivateMessage imMessage) {
        ImMessageTypeEnum imMessageTypeEnum =
                ImMessageAppTransformer.INSTANCE.imMessageTypeEnumFrom(imMessage.getContent().getType());
        ImPrivateMessageSentEvent imMessageSentEvent = new ImPrivateMessageSentEvent();
        imMessageSentEvent.setMessageId(imMessage.getId().getValue());
        imMessageSentEvent.setChatId(imMessage.getChatId().getValue());
        imMessageSentEvent.setSenderId(imMessage.getSenderId().getValue());
        imMessageSentEvent.setReceiverId(imMessage.getReceiverId().getValue());
        imMessageSentEvent.setMessageType(imMessageTypeEnum);
        imMessageSentEvent.setMessageContent(imMessage.getContent().getValue());
        imMessageSentEvent.setSendTime(imMessage.getSendTime());
        imMessageSentEvent.setCreateTime(LocalDateTime.now());
        return imMessageSentEvent;
    }

    public ImPrivateMessageRevokedEvent newImMessageRevokedEvent(ImPrivateMessage imMessage) {
        ImPrivateMessageRevokedEvent imMessageRevokedEvent = new ImPrivateMessageRevokedEvent();
        imMessageRevokedEvent.setChatId(imMessage.getId().getValue());
        imMessageRevokedEvent.setReceiverId(imMessage.getReceiverId().getValue());
        imMessageRevokedEvent.setMessageId(imMessage.getId().getValue());
        imMessageRevokedEvent.setCreateTime(LocalDateTime.now());
        return imMessageRevokedEvent;
    }

    public ImPrivateMessageReceivedEvent newImMessageReceivedEvent(ImPrivateMessage imMessage) {
        ImPrivateMessageReceivedEvent imMessageReceivedEvent = new ImPrivateMessageReceivedEvent();
        imMessageReceivedEvent.setChatId(imMessage.getChatId().getValue());
        imMessageReceivedEvent.setReceiverId(imMessage.getSenderId().getValue());
        imMessageReceivedEvent.setMessageId(imMessage.getId().getValue());
        imMessageReceivedEvent.setCreateTime(LocalDateTime.now());
        return imMessageReceivedEvent;
    }

    public ImPrivateMessageReadEvent newImMessageReadEvent(ImPrivateMessage imMessage) {
        ImPrivateMessageReadEvent imMessageReadEvent = new ImPrivateMessageReadEvent();
        imMessageReadEvent.setChatId(imMessage.getChatId().getValue());
        imMessageReadEvent.setReceiverId(imMessage.getSenderId().getValue());
        imMessageReadEvent.setMessageId(imMessage.getId().getValue());
        imMessageReadEvent.setCreateTime(LocalDateTime.now());
        return imMessageReadEvent;
    }


    public ImGroupMessageSentEvent newImMessageSentEvent(ImGroupMessage imMessage) {
        ImMessageTypeEnum imMessageTypeEnum =
                ImMessageAppTransformer.INSTANCE.imMessageTypeEnumFrom(imMessage.getContent().getType());
        ImGroupMessageSentEvent imMessageSentEvent = new ImGroupMessageSentEvent();
        imMessageSentEvent.setMessageId(imMessage.getId().getValue());
        imMessageSentEvent.setChatId(imMessage.getChatId().getValue());
        imMessageSentEvent.setSenderId(imMessage.getSenderId().getValue());
        imMessageSentEvent.setMessageType(imMessageTypeEnum);
        imMessageSentEvent.setMessageContent(imMessage.getContent().getValue());
        imMessageSentEvent.setSendTime(imMessage.getSendTime());
        imMessageSentEvent.setCreateTime(LocalDateTime.now());
        return imMessageSentEvent;
    }

    public ImGroupMessageRevokedEvent newImMessageRevokedEvent(ImGroupMessage imMessage) {
        ImGroupMessageRevokedEvent imMessageRevokedEvent = new ImGroupMessageRevokedEvent();
        imMessageRevokedEvent.setChatId(imMessage.getId().getValue());
        imMessageRevokedEvent.setMessageId(imMessage.getId().getValue());
        imMessageRevokedEvent.setCreateTime(LocalDateTime.now());
        return imMessageRevokedEvent;
    }
}
