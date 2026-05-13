package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.chat.ImChat;
import lombok.Getter;

@Getter
public class ImMessageRecipient {
    private final ImChat chat;
    private final boolean chatting;

    public ImMessageRecipient(ImChat chat, boolean chatting) {
        if (chat == null) {
            throw new IllegalArgumentException("接收者聊天不能为空");
        }
        this.chat = chat;
        this.chatting = chatting;
    }
}
