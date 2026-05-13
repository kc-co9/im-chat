package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.shared.DomainEvent;
import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
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
