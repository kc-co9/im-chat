package com.co.kc.imchat.model.io.chat;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ImGroupChatOpenRequest {
    @NotNull(message = "群聊不能为空")
    private Long chatId;
}
