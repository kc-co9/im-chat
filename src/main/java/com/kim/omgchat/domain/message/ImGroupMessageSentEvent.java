package com.kim.omgchat.domain.message;

import com.kim.omgchat.domain.shared.DomainEvent;
import com.kim.omgchat.model.enums.ImMessageTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImGroupMessageSentEvent implements DomainEvent {
    private Long messageId;
    private Long chatId;
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
