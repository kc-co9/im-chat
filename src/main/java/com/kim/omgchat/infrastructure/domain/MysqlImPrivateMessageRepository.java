package com.kim.omgchat.infrastructure.domain;

import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.message.ImMessageId;
import com.kim.omgchat.domain.message.ImPrivateMessage;
import com.kim.omgchat.domain.message.ImPrivateMessageRepository;
import com.kim.omgchat.domain.user.UserId;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class MysqlImPrivateMessageRepository implements ImPrivateMessageRepository {
    @Override
    public void save(ImPrivateMessage message) {

    }

    @Override
    public ImPrivateMessage find(ImChatId chatId, ImMessageId messageId) {
        return null;
    }

    @Override
    public List<ImPrivateMessage> queryHistory(ImChatId imChatId, UserId userId, ImMessageId lastMessageId, int count) {
        return Collections.emptyList();
    }
}
