package com.co.kc.imchat.application.model.cqrs.command.im;

import lombok.Data;

@Data
public class ImPrivateMessageRevokeCmd {
    private Long userId;
    private Long chatId;
    private Long messageId;
}
