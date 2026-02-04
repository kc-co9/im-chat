package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.chat.ImChat;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatRepository;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivatePair;
import com.co.kc.imchat.domain.user.UserId;
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
