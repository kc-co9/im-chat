package com.co.kc.imchat.domain.message.model;

import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.user.model.UserId;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

@Getter
public class ImGroupMessageTransmission {
    private final List<ImGroupInboxMessage> inboxMessages;
    private final List<ImGroupChat> groupChats;

    public ImGroupMessageTransmission(List<ImGroupInboxMessage> inboxMessages, List<ImGroupChat> groupChats) {
        if (CollectionUtils.isEmpty(inboxMessages)) {
            throw new IllegalArgumentException("群消息不能为空");
        }
        if (CollectionUtils.isEmpty(groupChats)) {
            throw new IllegalArgumentException("群聊会话不能为空");
        }
        this.inboxMessages = Collections.unmodifiableList(inboxMessages);
        this.groupChats = Collections.unmodifiableList(groupChats);
    }

    public ImGroupInboxMessage getSenderMessage(UserId senderId) {
        return inboxMessages.stream()
                .filter(message -> message.getUserId().equals(senderId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("发送者消息不存在"));
    }
}
