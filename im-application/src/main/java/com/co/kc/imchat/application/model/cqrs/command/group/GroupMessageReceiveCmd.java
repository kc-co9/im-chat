package com.co.kc.imchat.application.model.cqrs.command.group;

import lombok.Data;

@Data
public class GroupMessageReceiveCmd {
    private Long userId;
    private Long chatId;
    private Long messageId;
}
