package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface ImPrivateMessageRepository {

    void save(ImPrivateMessage message);

    ImPrivateMessage find(ImChatId chatId, ImMessageId messageId);

    List<ImPrivateMessage> queryHistory(ImChatId imChatId, ImMessageId lastMessageId, int count);
}
