package com.co.kc.imchat.model.io.chat;

import com.co.kc.imchat.model.enums.ImChatTypeEnum;
import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
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
