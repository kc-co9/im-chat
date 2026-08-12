package com.co.kc.imchat.service.message.domain.message.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImPrivateMessageRevokedEvent implements DomainEvent {
    private Long chatId;
    private Long receiverId;
    private Long messageId;
    private LocalDateTime createTime;

    @Override
    public LocalDateTime occurredOn() {
        return createTime;
    }
}
