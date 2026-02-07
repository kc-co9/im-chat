package com.co.kc.imchat.model.io.chat;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ImPrivateChatEnterRequest {
    @NotNull(message = "聊天ID不能为空")
    private Long chatId;
}
