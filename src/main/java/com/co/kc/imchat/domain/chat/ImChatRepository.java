package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface ImChatRepository {
    ImChat find(ImChatId chatId);

    ImChatLastMessage findLastMessage(ImChatId chatId);

    List<ImChatLastMessage> findLastMessageList(List<ImChatId> chatIds);

    List<ImChat> find(UserId userId);

    ImPrivateChat findPrivateChat(ImChatId chatId);

    ImPrivateChat findPrivateChat(ImPrivatePair pair);

    ImGroupChat findGroupChat(ImChatId chatId);

    void save(ImPrivateChat imPrivateChat);

    void save(ImGroupChat imGroupChat);
}
