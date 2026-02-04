package com.co.kc.imchat.model.cqrs.command.im;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImPrivateMessageReadCmd {
    private Long chatId;
    private Long userId;
    private Long messageId;
}
