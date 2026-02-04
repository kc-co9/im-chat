package com.kim.omgchat.model.cqrs.command.notify;

import lombok.Data;

@Data
public class ImGroupRevokedNotifyCmd {
    private Long chatId;
    private Long messageId;
    private Long receiverId;
}
