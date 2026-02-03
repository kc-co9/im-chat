package com.kim.omgchat.model.cqrs.dto.im;

import com.kim.omgchat.domain.chat.ImChatType;
import lombok.Data;

@Data
public class ImChatItemDTO {
    private Long chatId;
    private String chatName;
    private ImChatType chatType;
}
