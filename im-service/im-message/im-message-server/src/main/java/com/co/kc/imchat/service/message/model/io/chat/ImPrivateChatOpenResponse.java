package com.co.kc.imchat.service.message.model.io.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImPrivateChatOpenResponse {
    private Long chatId;
    private Long peerUserId;
}
