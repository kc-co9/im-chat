package com.co.kc.imchat.service.message.domain.message.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.service.message.domain.chat.model.ImChat;

/**
 * 值对象：消息接收上下文。
 */
public record ImMessageRecipient(ImChat chat, boolean chatting) {
    public ImMessageRecipient {
        AssertUtils.domainPropNotNull("接收者聊天不能为空", chat);
    }
}
