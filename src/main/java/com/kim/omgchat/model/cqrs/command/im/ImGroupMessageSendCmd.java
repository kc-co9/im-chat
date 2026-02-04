package com.kim.omgchat.model.cqrs.command.im;

import com.kim.omgchat.domain.message.ImMessageType;
import lombok.Data;

@Data
public class ImGroupMessageSendCmd {
    private Long chatId;
    private Long senderId;
    private String messageToken;
    private ImMessageType messageType;
    private String messageContent;
}
