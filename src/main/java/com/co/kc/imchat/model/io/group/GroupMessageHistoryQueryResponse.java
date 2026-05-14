package com.co.kc.imchat.model.io.group;

import com.co.kc.imchat.model.enums.ImMessageStatusEnum;
import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessageHistoryQueryResponse {
    private List<MessageItem> messageList;

    @Data
    public static class MessageItem {
        private Long messageId;
        private String token;
        private ImMessageTypeEnum type;
        private String content;
        private Long chatId;
        private Long senderId;
        private ImMessageStatusEnum status;
        private LocalDateTime sendTime;
        private LocalDateTime readTime;
        private LocalDateTime revokeTime;
    }
}
