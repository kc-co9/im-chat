package com.co.kc.imchat.domain.message.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.user.model.UserId;

import java.util.Collections;
import java.util.List;

/**
 * 值对象：群消息投递结果。
 */
public record ImGroupMessageTransmission(List<ImGroupInboxMessage> inboxMessages, List<ImGroupChat> groupChats) {
    public ImGroupMessageTransmission {
        AssertUtils.domainPropNotEmpty("群消息不能为空", inboxMessages);
        AssertUtils.domainPropNotEmpty("群聊会话不能为空", groupChats);
        inboxMessages = Collections.unmodifiableList(inboxMessages);
        groupChats = Collections.unmodifiableList(groupChats);
    }

    public ImGroupInboxMessage getSenderMessage(UserId senderId) {
        return inboxMessages.stream()
                .filter(message -> message.getUserId().equals(senderId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("发送者消息不存在"));
    }
}
