package com.kim.omgchat.infrastructure.domain;

import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.message.ImMessageId;
import com.kim.omgchat.domain.message.ImPrivateMessage;
import com.kim.omgchat.domain.message.ImPrivateMessageRepository;
import org.springframework.stereotype.Repository;

@Repository
public class MysqlImPrivateMessageRepository implements ImPrivateMessageRepository {
    @Override
    public void save(ImPrivateMessage message) {

    }

    @Override
    public ImPrivateMessage find(ImChatId chatId, ImMessageId messageId) {
        return null;
    }
}
