package com.co.kc.imchat.interfaces.model.io.chat;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ImPrivateChatOpenRequest {
    @NotNull(message = "聊天对象不能为空")
    private Long peerUserId;
}
