package com.co.kc.imchat.domain.message;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ImOutboundMessage {
    private final ImMessageId id;
    private final ImMessageToken token;
    private final ImMessageContent content;
    private final LocalDateTime sendTime;

    public ImOutboundMessage(ImMessageId id, ImMessageToken token, ImMessageContent content) {
        this(id, token, content, LocalDateTime.now());
    }

    public ImOutboundMessage(ImMessageId id,
                             ImMessageToken token,
                             ImMessageContent content,
                             LocalDateTime sendTime) {
        if (id == null || token == null || content == null || sendTime == null) {
            throw new IllegalArgumentException("出站消息缺少 id、token、content 或 sendTime");
        }
        this.id = id;
        this.token = token;
        this.content = content;
        this.sendTime = sendTime;
    }
}
