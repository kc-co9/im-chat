package com.co.kc.imchat.model.io.chat;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ImPrivateChatCreateRequest {
    @NotNull(message = "接收者ID不能为空")
    private Long receiverId;
}
