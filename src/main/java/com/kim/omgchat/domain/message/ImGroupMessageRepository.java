package com.kim.omgchat.domain.message;

import com.kim.omgchat.domain.chat.ImChatId;

public interface ImGroupMessageRepository {
    void save(ImGroupMessage imMessage);

    ImGroupMessage find(ImChatId chatId, ImMessageId messageId);
}
