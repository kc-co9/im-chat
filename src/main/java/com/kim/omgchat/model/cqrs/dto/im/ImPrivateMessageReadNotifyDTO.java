package com.kim.omgchat.model.cqrs.dto.im;

import lombok.Data;

@Data
public class ImPrivateMessageReadNotifyDTO {
    private Long chatId;
    private Long receiverId;
    private Long messageId;
}
