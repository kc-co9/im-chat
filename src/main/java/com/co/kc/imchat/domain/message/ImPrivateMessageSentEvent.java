package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.shared.DomainEvent;
import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领域事件-消息发送
 */
@Data
public class ImPrivateMessageSentEvent implements DomainEvent {
    private Long messageId;
    private Long senderId;
    private Long receiverId;
    private Long receiverChatId;
    private ImMessageTypeEnum messageType;
    private String messageContent;
    private LocalDateTime sendTime;
    private LocalDateTime createTime;

    @Override
    public LocalDateTime occurredOn() {
        return createTime;
    }
}
