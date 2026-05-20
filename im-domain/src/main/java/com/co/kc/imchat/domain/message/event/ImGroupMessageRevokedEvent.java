package com.co.kc.imchat.domain.message.event;

import com.co.kc.imchat.domain.shared.event.DomainEvent;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImGroupMessageRevokedEvent implements DomainEvent {
    private Long groupId;
    private Long messageId;
    private Long senderId;
    private LocalDateTime revokeTime;
    private LocalDateTime createTime;

    @Override
    public LocalDateTime occurredOn() {
        return createTime;
    }
}
