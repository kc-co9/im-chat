package com.co.kc.imchat.application.model.cqrs.dto.group;

import com.co.kc.imchat.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.model.ImMessageType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupMessageDTO {
    private Long messageId;
    private String token;
    private ImMessageType type;
    private String content;
    private Long chatId;
    private Long senderId;
    private ImGroupMessageStatus status;
    private LocalDateTime sendTime;
    private LocalDateTime readTime;
    private LocalDateTime revokeTime;
}
