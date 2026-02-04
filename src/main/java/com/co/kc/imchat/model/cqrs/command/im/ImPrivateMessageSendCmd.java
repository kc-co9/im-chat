package com.co.kc.imchat.model.cqrs.command.im;

import com.co.kc.imchat.domain.message.ImMessageType;
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
