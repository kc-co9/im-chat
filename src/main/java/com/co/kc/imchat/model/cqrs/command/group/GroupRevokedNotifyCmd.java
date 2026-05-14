package com.co.kc.imchat.model.cqrs.command.group;

import lombok.Data;

@Data
public class GroupRevokedNotifyCmd {
    private Long chatId;
    private Long messageId;
    private Long receiverId;
}
