package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.chat.ImChatId;

import java.util.List;

public interface ImGroupMessageRepository {
    void save(ImGroupMessage imMessage);

    ImGroupMessage find(ImChatId chatId, ImMessageId messageId);

    List<ImGroupMessage> queryHistory(ImChatId chatId, ImMessageId imLastMessageId, Integer count);

    ImGroupMessage queryDetail(ImChatId chatId, ImMessageToken messageToken);
}
