package com.co.kc.imchat.application.model.cqrs.command.group;

import com.co.kc.imchat.domain.message.model.ImMessageType;
import lombok.Data;

@Data
public class GroupMessageSendCmd {
    private Long chatId;
    private Long senderId;
    private String messageToken;
    private ImMessageType messageType;
    private String messageContent;
}
