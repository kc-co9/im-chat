package com.co.kc.imchat.model.cqrs.command.group;

import lombok.Data;

@Data
public class GroupMessageReadCmd {
    private Long userId;
    private Long chatId;
    private Long messageId;
}
