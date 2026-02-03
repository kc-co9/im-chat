package com.kim.omgchat.model.cqrs.query;

import lombok.Data;

@Data
public class ImPrivateMessageHistoryQuery {
    private Long chatId;
    private Long userId;
    private Integer pageNum;
    private Integer pageSize;
}
