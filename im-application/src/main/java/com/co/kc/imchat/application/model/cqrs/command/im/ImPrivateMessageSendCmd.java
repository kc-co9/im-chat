package com.co.kc.imchat.application.model.cqrs.command.im;

import com.co.kc.imchat.domain.message.model.ImMessageType;
import lombok.Data;

@Data
public class ImPrivateMessageSendCmd {
    private Long userId;
    private Long chatId;
    private String messageToken;
    private ImMessageType messageType;
    private String messageContent;
}
