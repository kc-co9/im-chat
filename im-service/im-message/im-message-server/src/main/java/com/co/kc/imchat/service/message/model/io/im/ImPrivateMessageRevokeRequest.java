package com.co.kc.imchat.service.message.model.io.im;

import lombok.Data;

@Data
public class ImPrivateMessageRevokeRequest {
    private Long chatId;
    private Long messageId;
}
