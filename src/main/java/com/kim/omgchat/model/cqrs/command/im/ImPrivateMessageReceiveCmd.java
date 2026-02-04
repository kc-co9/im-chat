package com.kim.omgchat.model.cqrs.command.im;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImPrivateMessageReceiveCmd {
    private Long chatId;
    private Long userId;
    private Long messageId;
}
