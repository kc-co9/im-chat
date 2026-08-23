package com.co.kc.imchat.service.message.domain.message.repository;

import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.common.domain.user.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * 资源库：群聊收件箱消息。
 */
public interface ImGroupInboxMessageRepository {

    void save(ImGroupInboxMessage message);

    void save(List<ImGroupInboxMessage> messages);

    boolean contain(ImChatId chatId, UserId userId, ImMessageToken token);

    Optional<ImGroupInboxMessage> find(ImChatId chatId, UserId userId, ImMessageId messageId);

    List<ImGroupInboxMessage> findByGroupIdAndMessageId(GroupId groupId, ImMessageId messageId);

    List<ImGroupInboxMessage> findUnreadMessages(ImChatId chatId, UserId userId);

    List<ImGroupInboxMessage> queryHistory(ImChatId chatId, UserId userId, ImMessageId lastMessageId, Integer count);

    ImGroupInboxMessage queryDetail(ImChatId chatId, UserId userId, ImMessageToken token);

    List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId userId);
}
