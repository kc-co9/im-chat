package com.co.kc.imchat.model.cqrs.command.notify;

import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImPrivateSentNotifyCmd {
    private Long messageId;
    private Long chatId;
    private Long senderId;
    private Long receiverId;
    private ImMessageTypeEnum messageType;
    private String messageContent;
    private LocalDateTime sendTime;
}
