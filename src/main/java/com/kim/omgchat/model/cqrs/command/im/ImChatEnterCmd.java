package com.kim.omgchat.model.cqrs.command.im;

import lombok.Data;

@Data
public class ImChatEnterCmd {
    private Long userId;
    private Long chatId;
}
