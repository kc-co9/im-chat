package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.chat.ImChatId;

public interface ImGroupMessageRepository {
    void save(ImGroupMessage imMessage);

    ImGroupMessage find(ImChatId chatId, ImMessageId messageId);
}
