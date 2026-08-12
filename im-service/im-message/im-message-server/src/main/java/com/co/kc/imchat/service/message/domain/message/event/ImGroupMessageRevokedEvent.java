package com.co.kc.imchat.service.message.domain.message.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;
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
