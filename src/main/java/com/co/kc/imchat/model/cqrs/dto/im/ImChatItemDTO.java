package com.co.kc.imchat.model.cqrs.dto.im;

import com.co.kc.imchat.domain.chat.ImChatType;
import lombok.Data;

@Data
public class ImChatItemDTO {
    private Long chatId;
    private String chatName;
    private ImChatType chatType;
}
