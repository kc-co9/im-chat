package com.co.kc.imchat.model.io.chat;

import com.co.kc.imchat.model.enums.ImChatTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    }
}
