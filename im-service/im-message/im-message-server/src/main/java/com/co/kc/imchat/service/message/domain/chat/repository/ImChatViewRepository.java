package com.co.kc.imchat.service.message.domain.chat.repository;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatView;

import java.util.Optional;

/** 当前聊天查看状态仓储端口。 */
public interface ImChatViewRepository {
    void save(ImChatView presence);

    void clear(UserId userId);

    Optional<ImChatView> find(UserId userId);

    default boolean isViewing(UserId userId, ImChatId chatId) {
        return find(userId).map(view -> view.matches(chatId)).orElse(false);
    }
}
