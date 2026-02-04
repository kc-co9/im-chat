package com.co.kc.imchat.model.cqrs.command.im;

import lombok.Data;

@Data
public class ImGroupMessageRevokeCmd {
    private Long userId;
    private Long chatId;
    private Long messageId;
}
