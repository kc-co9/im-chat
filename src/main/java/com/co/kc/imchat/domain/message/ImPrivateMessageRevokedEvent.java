package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.shared.DomainEvent;
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
