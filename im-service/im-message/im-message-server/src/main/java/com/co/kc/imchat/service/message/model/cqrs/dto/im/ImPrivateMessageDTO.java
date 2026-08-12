package com.co.kc.imchat.service.message.model.cqrs.dto.im;

import com.co.kc.imchat.service.message.domain.message.model.ImPrivateMessageStatus;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImPrivateMessageDTO {
    private Long messageId;
    private String token;
    private ImMessageType type;
    private String content;
    private Long chatId;
    private Long senderId;
    private Long receiverId;
    private ImPrivateMessageStatus status;
    private LocalDateTime sendTime;
    private LocalDateTime readTime;
    private LocalDateTime revokeTime;
}
