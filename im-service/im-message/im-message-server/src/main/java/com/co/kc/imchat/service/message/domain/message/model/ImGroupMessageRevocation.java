package com.co.kc.imchat.service.message.domain.message.model;

import java.util.List;

/**
 * 值对象：群消息撤回结果。
 */
public record ImGroupMessageRevocation(List<ImGroupInboxMessage> messages, ImGroupInboxMessage senderMessage) {
    public ImGroupMessageRevocation {
        messages = List.copyOf(messages);
    }
}
