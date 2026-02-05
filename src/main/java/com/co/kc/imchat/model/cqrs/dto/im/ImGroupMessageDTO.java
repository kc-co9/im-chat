package com.co.kc.imchat.model.cqrs.dto.im;

import com.co.kc.imchat.domain.message.ImMessageStatus;
import com.co.kc.imchat.domain.message.ImMessageType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImGroupMessageDTO {
    private Long messageId;
    private String token;
    private ImMessageType type;
    private String content;
    private Long chatId;
    private Long senderId;
    private ImMessageStatus status;
    private LocalDateTime sendTime;
    private LocalDateTime revokeTime;
}
