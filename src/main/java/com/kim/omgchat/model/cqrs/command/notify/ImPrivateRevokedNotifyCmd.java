package com.kim.omgchat.model.cqrs.command.notify;

import lombok.Data;

@Data
public class ImPrivateRevokedNotifyCmd {
    private Long chatId;
    private Long receiverId;
    private Long messageId;
}
