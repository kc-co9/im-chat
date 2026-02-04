package com.kim.omgchat.infrastructure.domain;

import com.kim.omgchat.domain.chat.ImChat;
import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.chat.ImChatRepository;
import com.kim.omgchat.domain.chat.ImGroupChat;
import com.kim.omgchat.domain.chat.ImPrivateChat;
import com.kim.omgchat.domain.chat.ImPrivatePair;
import com.kim.omgchat.domain.user.UserId;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class MysqlImChatRepository implements ImChatRepository {
    @Override
    public ImChat find(ImChatId chatId) {
        return null;
    }

    @Override
    public List<ImChat> find(UserId userId) {
        return Collections.emptyList();
    }

    @Override
    public ImPrivateChat findPrivateChat(ImPrivatePair pair) {
        return null;
    }

    @Override
    public ImGroupChat findGroupChat(ImChatId chatId) {
        return null;
    }

    @Override
    public void save(ImChat imChat) {

    }
}
