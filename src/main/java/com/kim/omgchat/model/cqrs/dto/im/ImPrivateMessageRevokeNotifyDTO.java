package com.kim.omgchat.model.cqrs.dto.im;

import lombok.Data;

@Data
public class ImPrivateMessageRevokeNotifyDTO {
    private Long chatId;
    private Long receiverId;
    private Long messageId;
}
