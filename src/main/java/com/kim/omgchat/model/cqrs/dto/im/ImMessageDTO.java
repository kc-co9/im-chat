package com.kim.omgchat.model.cqrs.dto.im;

import com.kim.omgchat.domain.message.ImMessageStatus;
import com.kim.omgchat.domain.message.ImMessageType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImMessageDTO {
    private Long messageId;
    private String token;
    private ImMessageType type;
    private String content;
    private Long chatId;
    private Long senderId;
    private Long receiverId;
    private ImMessageStatus status;
    private LocalDateTime sendTime;
    private LocalDateTime readTime;
    private LocalDateTime revokeTime;
}
