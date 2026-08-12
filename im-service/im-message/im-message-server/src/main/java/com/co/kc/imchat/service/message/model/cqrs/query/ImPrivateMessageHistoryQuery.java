package com.co.kc.imchat.service.message.model.cqrs.query;

public record ImPrivateMessageHistoryQuery(
        /* 聊天ID */
        Long chatId,
        /* 用户ID */
        Long userId,
        /* 上一页最后一条消息ID */
        Long lastMessageId,
        /* 查询数量 */
        Integer count
) {
}
