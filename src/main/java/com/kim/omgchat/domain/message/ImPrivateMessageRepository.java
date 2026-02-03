package com.kim.omgchat.domain.message;

import com.kim.omgchat.domain.chat.ImChatId;

public interface ImPrivateMessageRepository {

    void save(ImPrivateMessage message);

    ImPrivateMessage find(ImChatId chatId, ImMessageId messageId);
}
