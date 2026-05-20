package com.co.kc.imchat.application.model.cqrs.command.im;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImPrivateMessageReceiveCmd {
    private Long chatId;
    private Long userId;
    private Long messageId;
}
