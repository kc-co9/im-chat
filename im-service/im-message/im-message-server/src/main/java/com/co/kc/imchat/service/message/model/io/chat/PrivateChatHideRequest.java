package com.co.kc.imchat.service.message.model.io.chat;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class PrivateChatHideRequest {
    @NotNull(message = "聊天不能为空")
    private Long chatId;
}
