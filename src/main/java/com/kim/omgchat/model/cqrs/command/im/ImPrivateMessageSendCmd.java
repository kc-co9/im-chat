package com.kim.omgchat.model.cqrs.command.im;

import com.kim.omgchat.domain.message.ImMessageType;
import lombok.Data;

@Data
public class ImPrivateMessageSendCmd {
    private Long chatId;
    private Long senderId;
    private Long receiverId;
    private String messageToken;
    private ImMessageType messageType;
    private String messageContent;
}
