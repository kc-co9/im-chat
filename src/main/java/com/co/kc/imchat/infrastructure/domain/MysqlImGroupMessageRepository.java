package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.message.ImGroupMessage;
import com.co.kc.imchat.domain.message.ImGroupMessageRepository;
import com.co.kc.imchat.domain.message.ImMessageId;
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
