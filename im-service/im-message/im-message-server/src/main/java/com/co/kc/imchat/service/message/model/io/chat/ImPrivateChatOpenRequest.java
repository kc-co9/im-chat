package com.co.kc.imchat.service.message.model.io.chat;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class ImPrivateChatOpenRequest {
    @NotNull(message = "聊天对象不能为空")
    private Long peerUserId;
}
