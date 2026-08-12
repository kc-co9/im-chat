package com.co.kc.imchat.service.message.model.io.group;

import lombok.Data;

@Data
public class GroupMessageReadRequest {
    private Long chatId;
    private Long messageId;
}
