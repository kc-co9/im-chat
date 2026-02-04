package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface ImChatRepository {
    ImChat find(ImChatId chatId);

    List<ImChat> find(UserId userId);
    
    ImPrivateChat findPrivateChat(ImPrivatePair pair);

    ImGroupChat findGroupChat(ImChatId chatId);

    void save(ImChat imChat);
}
