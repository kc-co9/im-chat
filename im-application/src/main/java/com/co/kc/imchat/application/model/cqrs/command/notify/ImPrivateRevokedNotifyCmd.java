package com.co.kc.imchat.application.model.cqrs.command.notify;

import lombok.Data;

@Data
public class ImPrivateRevokedNotifyCmd {
    private Long receiverId;
    private Long receiverChatId;
    private Long messageId;
}
