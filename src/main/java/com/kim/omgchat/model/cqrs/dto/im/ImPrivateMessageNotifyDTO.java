package com.kim.omgchat.model.cqrs.dto.im;

import com.kim.omgchat.model.enums.ImMessageType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImPrivateMessageNotifyDTO {
    private Long messageId;
    private Long chatId;
    private Long senderId;
    private Long receiverId;
    private ImMessageType messageType;
    private String messageContent;
    private LocalDateTime sendTime;
}
