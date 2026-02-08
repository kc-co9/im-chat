package com.co.kc.imchat.model.io.im;

import com.co.kc.imchat.model.enums.ImMessageStatusEnum;
import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImGroupMessageDetailResponse {
    private Long messageId;
    private String token;
    private ImMessageTypeEnum type;
    private String content;
    private Long chatId;
    private Long senderId;
    private ImMessageStatusEnum status;
    private LocalDateTime sendTime;
    private LocalDateTime revokeTime;
}
