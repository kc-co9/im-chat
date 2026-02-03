package com.kim.omgchat.domain.message;

import com.kim.omgchat.domain.shared.DomainEvent;
import com.kim.omgchat.model.enums.ImMessageTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领域事件-消息发送
 */
@Data
public class ImMessageSentEvent implements DomainEvent {
    private Long messageId;
    private Long chatId;
    private Long senderId;
    private Long receiverId;
    private ImMessageTypeEnum messageType;
    private String messageContent;
    private LocalDateTime sendTime;
    private LocalDateTime createTime;

    @Override
    public LocalDateTime occurredOn() {
        return createTime;
    }
}
