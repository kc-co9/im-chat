package com.co.kc.imchat.service.message.model.io.im;

import com.co.kc.imchat.service.message.model.enums.ImMessageStatusEnum;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImPrivateMessageDetailQueryResponse {
    private Long messageId;
    private String token;
    private ImMessageTypeEnum type;
    private String content;
    private Long chatId;
    private Long senderId;
    private Long receiverId;
    private ImMessageStatusEnum status;
    private LocalDateTime sendTime;
    private LocalDateTime readTime;
    private LocalDateTime revokeTime;
}
