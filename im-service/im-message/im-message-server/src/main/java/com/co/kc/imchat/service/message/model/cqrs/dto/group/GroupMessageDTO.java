package com.co.kc.imchat.service.message.model.cqrs.dto.group;

import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
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
