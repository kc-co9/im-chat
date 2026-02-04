package com.kim.omgchat.domain.message;

import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.user.UserId;

import java.util.List;

public interface ImPrivateMessageRepository {

    void save(ImPrivateMessage message);

    ImPrivateMessage find(ImChatId chatId, ImMessageId messageId);

    List<ImPrivateMessage> queryHistory(ImChatId imChatId, UserId userId, ImMessageId lastMessageId, int count);
}
