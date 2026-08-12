package com.co.kc.imchat.service.message.model.io.group;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class GroupChatOpenRequest {
    @NotNull(message = "群聊不能为空")
    private Long chatId;
}
