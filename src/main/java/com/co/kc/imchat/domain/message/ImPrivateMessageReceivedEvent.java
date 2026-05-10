package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.shared.DomainEvent;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImPrivateMessageReceivedEvent implements DomainEvent {
    private Long receiverId;
    private Long receiverChatId;
    private Long messageId;
    private LocalDateTime createTime;

    @Override
    public LocalDateTime occurredOn() {
        return createTime;
    }
}
