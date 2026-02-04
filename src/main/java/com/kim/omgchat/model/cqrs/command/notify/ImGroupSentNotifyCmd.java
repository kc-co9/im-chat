package com.kim.omgchat.model.cqrs.command.notify;

import com.kim.omgchat.model.enums.ImMessageTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImGroupSentNotifyCmd {
    private Long messageId;
    private Long chatId;
    private Long senderId;
    private Long receiverId;
    private ImMessageTypeEnum messageType;
    private String messageContent;
    private LocalDateTime sendTime;
}
