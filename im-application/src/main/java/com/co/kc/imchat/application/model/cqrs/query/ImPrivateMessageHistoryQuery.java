package com.co.kc.imchat.application.model.cqrs.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImPrivateMessageHistoryQuery {
    private Long chatId;
    private Long userId;
    private Long lastMessageId;
    private Integer count;
}
