package com.kim.omgchat.domain.chat;

import com.kim.omgchat.domain.user.UserId;

import java.util.List;

public interface ImChatRepository {
    ImChat find(ImChatId chatId);

    List<ImChat> find(UserId userId);
    
    ImPrivateChat findPrivateChat(ImPrivatePair pair);

    ImGroupChat findGroupChat(ImChatId chatId);

    void save(ImChat imChat);
}
