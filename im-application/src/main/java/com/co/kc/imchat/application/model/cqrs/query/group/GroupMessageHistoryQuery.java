package com.co.kc.imchat.application.model.cqrs.query.group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessageHistoryQuery {
    private Long chatId;
    private Long userId;
    private Long lastMessageId;
    private Integer count;
}
