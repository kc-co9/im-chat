package com.kim.omgchat.infrastructure.domain;

import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.message.ImGroupMessage;
import com.kim.omgchat.domain.message.ImGroupMessageRepository;
import com.kim.omgchat.domain.message.ImMessageId;
import org.springframework.stereotype.Repository;

@Repository
public class MysqlImGroupMessageRepository implements ImGroupMessageRepository {
    @Override
    public void save(ImGroupMessage imMessage) {

    }

    @Override
    public ImGroupMessage find(ImChatId chatId, ImMessageId messageId) {
        return null;
    }
}
