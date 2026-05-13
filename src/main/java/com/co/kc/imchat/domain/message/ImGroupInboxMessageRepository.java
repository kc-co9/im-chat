package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImGroupId;
import com.co.kc.imchat.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public interface ImGroupInboxMessageRepository {

    void save(ImGroupInboxMessage message);

    void saveAll(List<ImGroupInboxMessage> messages);

    boolean contain(ImChatId chatId, UserId userId, ImMessageToken token);

    Optional<ImGroupInboxMessage> find(ImChatId chatId, UserId userId, ImMessageId messageId);

    List<ImGroupInboxMessage> findByGroupIdAndMessageId(ImGroupId groupId, ImMessageId messageId);

    List<ImGroupInboxMessage> findUnreadMessages(ImChatId chatId, UserId userId);

    List<ImGroupInboxMessage> queryHistory(ImChatId chatId, UserId userId, ImMessageId lastMessageId, Integer count);

    ImGroupInboxMessage queryDetail(ImChatId chatId, UserId userId, ImMessageToken token);

    List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId userId);
}
