package com.co.kc.imchat.domain.message.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.LocalDateTime;

/**
 * 值对象：待投递的出站消息。
 */
public record ImOutboundMessage(
        ImMessageId id,
        ImMessageToken token,
        ImMessageContent content,
        LocalDateTime sendTime) {
    public ImOutboundMessage(ImMessageId id, ImMessageToken token, ImMessageContent content) {
        this(id, token, content, LocalDateTime.now());
    }

    public ImOutboundMessage {
        AssertUtils.allDomainPropNotNull("出站消息缺少 id、token、content 或 sendTime", id, token, content, sendTime);
    }
}
