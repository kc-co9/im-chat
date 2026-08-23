package com.co.kc.imchat.service.message.domain.message.repository;

import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.common.domain.user.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * 资源库：私聊收件箱消息。
 */
public interface ImPrivateInboxMessageRepository {

    void save(ImPrivateInboxMessage message);

    void saveBatch(List<ImPrivateInboxMessage> messages);

    Optional<ImPrivateInboxMessage> find(ImChatId chatId, ImMessageId messageId);

    Optional<ImPrivateInboxMessage> find(ImChatId chatId, ImMessageToken messageToken);

    List<ImPrivateInboxMessage> queryHistory(ImChatId imChatId, ImMessageId imLastMessageId, int count, UserId viewer);

    Optional<ImPrivateInboxMessage> queryDetail(ImChatId chatId, ImMessageToken messageToken, UserId viewer);

    boolean contain(ImChatId chatId, ImMessageToken messageToken);
}
