package com.co.kc.imchat.domain.message.model;

import com.co.kc.imchat.domain.chat.model.ImChat;
import com.co.kc.imchat.domain.user.model.UserId;
import lombok.Getter;

@Getter
public class ImMessageSender {
    private final ImChat chat;
    private final UserId userId;

    public ImMessageSender(ImChat chat, UserId userId) {
        if (chat == null) {
            throw new IllegalArgumentException("发送者聊天不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("发送者不能为空");
        }
        this.chat = chat;
        this.userId = userId;
    }
}
