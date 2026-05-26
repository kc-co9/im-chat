package com.co.kc.imchat.domain.message.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.domain.chat.model.ImChat;
import com.co.kc.imchat.domain.user.model.UserId;

/**
 * 值对象：消息发送上下文。
 */
public record ImMessageSender(ImChat chat, UserId userId) {
    public ImMessageSender {
        AssertUtils.domainPropNotNull("发送者聊天不能为空", chat);
        AssertUtils.domainPropNotNull("发送者不能为空", userId);
    }
}
