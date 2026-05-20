package com.co.kc.imchat.application.model.cqrs.command.group;

import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupSentNotifyCmd {
    private Long messageId;
    private Long chatId;
    private Long senderId;
    private Long receiverId;
    private ImMessageTypeEnum messageType;
    private String messageContent;
    private LocalDateTime sendTime;
}
