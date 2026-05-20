package com.co.kc.imchat.interfaces.model.io.chat;

import com.co.kc.imchat.interfaces.model.enums.ImChatTypeEnum;
import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImChatListResponse {

    private List<ImChatItem> chatList;

    @Data
    public static class ImChatItem {
        private Long chatId;
        private String chatName;
        private ImChatTypeEnum chatType;
        private ImMessageTypeEnum lastMessageType;
        private String lastMessageContent;
        private LocalDateTime lastMessageTime;
    }
}
