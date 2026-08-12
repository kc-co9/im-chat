package com.co.kc.imchat.service.message.model.cqrs.dto.im;

import com.co.kc.imchat.service.message.domain.chat.model.ImChatType;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImChatItemDTO {
    private Long chatId;
    private String chatName;
    private ImChatType chatType;
    private ImMessageTypeEnum lastMessageType;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
}
