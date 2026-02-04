package com.co.kc.imchat.model.cqrs.command.im;

import com.co.kc.imchat.domain.message.ImMessageType;
import lombok.Data;

@Data
public class ImGroupMessageSendCmd {
    private Long chatId;
    private Long senderId;
    private String messageToken;
    private ImMessageType messageType;
    private String messageContent;
}
