package com.co.kc.imchat.service.message.domain.chat.repository;

import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.domain.message.model.ImMessage;
import com.co.kc.imchat.common.domain.user.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * 资源库：私聊会话。
 */
public interface ImPrivateChatRepository {

    List<ImPrivateChat> find(UserId userId);

    Optional<ImPrivateChat> find(ImChatId chatId);

    /**
     * 按「拥有者 + 对端」查找该用户视角下的私聊记录（每人一条 db 行）。
     */
    Optional<ImPrivateChat> find(UserId userId, UserId peerUserId);

    boolean contain(UserId userId, UserId peerUserId);

    List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer);

    void save(ImPrivateChat imPrivateChat);

    void remove(UserId userId, UserId peerUserId);
}
