package com.co.kc.imchat.domain.message.repository;

import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.message.model.ImMessageToken;
import com.co.kc.imchat.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.user.model.UserId;

import java.util.List;
import java.util.Optional;

public interface ImPrivateInboxMessageRepository {

    void save(ImPrivateInboxMessage message);

    void saveBatch(List<ImPrivateInboxMessage> messages);

    Optional<ImPrivateInboxMessage> find(ImChatId chatId, ImMessageId messageId);

    Optional<ImPrivateInboxMessage> find(ImChatId chatId, ImMessageToken messageToken);

    List<ImPrivateInboxMessage> queryHistory(ImChatId imChatId, ImMessageId imLastMessageId, int count, UserId viewer);

    Optional<ImPrivateInboxMessage> queryDetail(ImChatId chatId, ImMessageToken messageToken, UserId viewer);

    boolean contain(ImChatId chatId, ImMessageToken messageToken);
}
