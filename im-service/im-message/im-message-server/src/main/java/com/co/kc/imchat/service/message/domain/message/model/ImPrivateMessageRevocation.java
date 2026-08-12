package com.co.kc.imchat.service.message.domain.message.model;

import java.util.Arrays;
import java.util.List;

/**
 * 值对象：私聊消息撤回结果。
 */
public record ImPrivateMessageRevocation(ImPrivateInboxMessage senderMessage, ImPrivateInboxMessage receiverMessage) {
    public List<ImPrivateInboxMessage> getMessages() {
        return Arrays.asList(senderMessage, receiverMessage);
    }
}
