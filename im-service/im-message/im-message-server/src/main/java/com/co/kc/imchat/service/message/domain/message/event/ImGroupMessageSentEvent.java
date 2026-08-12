package com.co.kc.imchat.service.message.domain.message.event;

import com.co.kc.imchat.service.message.domain.message.model.ImMessageTypeEnum;
import com.co.kc.imchat.common.domain.shared.event.DomainEvent;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImGroupMessageSentEvent implements DomainEvent {
    private Long messageId;
    private Long groupId;
    private Long senderId;
    private ImMessageTypeEnum messageType;
    private String messageContent;
    private LocalDateTime sendTime;
    private LocalDateTime createTime;

    @Override
    public LocalDateTime occurredOn() {
        return createTime;
    }
}
