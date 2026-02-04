package com.kim.omgchat.domain.message;

import com.kim.omgchat.model.enums.ImMessageTypeEnum;
import com.kim.omgchat.transformer.ImMessageAppTransformer;

import java.time.LocalDateTime;

/**
 * IM消息-领域服务
 */
public class ImMessageService {
    public ImMessageSentEvent newImMessageSentEvent(ImPrivateMessage imMessage) {
        ImMessageTypeEnum imMessageTypeEnum =
                ImMessageAppTransformer.INSTANCE.imMessageTypeEnumFrom(imMessage.getContent().getType());
        ImMessageSentEvent imMessageSentEvent = new ImMessageSentEvent();
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

    public ImMessageRevokedEvent newImMessageRevokedEvent(ImPrivateMessage imMessage) {
        ImMessageRevokedEvent imMessageRevokedEvent = new ImMessageRevokedEvent();
        imMessageRevokedEvent.setChatId(imMessage.getId().getValue());
        imMessageRevokedEvent.setReceiverId(imMessage.getReceiverId().getValue());
        imMessageRevokedEvent.setMessageId(imMessage.getId().getValue());
        imMessageRevokedEvent.setCreateTime(LocalDateTime.now());
        return imMessageRevokedEvent;
    }

    public ImMessageReceivedEvent newImMessageReceivedEvent(ImPrivateMessage imMessage) {
        ImMessageReceivedEvent imMessageReceivedEvent = new ImMessageReceivedEvent();
        imMessageReceivedEvent.setChatId(imMessage.getChatId().getValue());
        imMessageReceivedEvent.setReceiverId(imMessage.getSenderId().getValue());
        imMessageReceivedEvent.setMessageId(imMessage.getId().getValue());
        imMessageReceivedEvent.setCreateTime(LocalDateTime.now());
        return imMessageReceivedEvent;
    }

    public ImMessageReadEvent newImMessageReadEvent(ImPrivateMessage imMessage) {
        ImMessageReadEvent imMessageReadEvent = new ImMessageReadEvent();
        imMessageReadEvent.setChatId(imMessage.getChatId().getValue());
        imMessageReadEvent.setReceiverId(imMessage.getSenderId().getValue());
        imMessageReadEvent.setMessageId(imMessage.getId().getValue());
        imMessageReadEvent.setCreateTime(LocalDateTime.now());
        return imMessageReadEvent;
    }


}
