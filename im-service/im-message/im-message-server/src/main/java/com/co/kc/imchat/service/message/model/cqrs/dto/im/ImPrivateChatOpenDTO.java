package com.co.kc.imchat.service.message.model.cqrs.dto.im;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImPrivateChatOpenDTO {
    private Long chatId;
    private Long peerUserId;
}
