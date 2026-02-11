package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.chat.ImChatId;

import java.util.List;

public interface ImPrivateMessageRepository {

    void save(ImPrivateMessage message);

    ImPrivateMessage find(ImChatId chatId, ImMessageId messageId);

    ImPrivateMessage find(ImChatId chatId, ImMessageToken messageToken);

    List<ImPrivateMessage> queryHistory(ImChatId imChatId, ImMessageId imLastMessageId, int count);

    ImPrivateMessage queryDetail(ImChatId chatId, ImMessageToken messageToken);
}
