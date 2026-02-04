package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImPrivateMessage;
import com.co.kc.imchat.domain.message.ImPrivateMessageRepository;
import com.co.kc.imchat.domain.user.UserId;
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
