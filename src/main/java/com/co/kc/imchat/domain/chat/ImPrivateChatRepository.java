package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface ImPrivateChatRepository {

    List<ImPrivateChat> find(UserId userId);

    ImPrivateChat find(ImChatId chatId);

    /**
     * 按「拥有者 + 对端」查找该用户视角下的私聊记录（每人一条 db 行）。
     */
    ImPrivateChat find(UserId userId, UserId peerUserId);

    List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer);

    void save(ImPrivateChat imPrivateChat);
}
