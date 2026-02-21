package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface ImChatRepository {

    List<ImPrivateChat> findPrivateChatList(UserId userId);

    ImPrivateChat findPrivateChat(ImChatId chatId);

    ImPrivateChat findPrivateChat(ImPrivatePair pair);

    List<ImGroupChat> findGroupChatList(UserId userId);

    List<ImGroupMember> findGroupMemberList(ImChatId chatId);

    List<ImGroupMember> findUserGroupMemberList(UserId userId, List<ImChatId> chatIds);

    ImGroupChat findGroupChat(ImChatId chatId);

    List<ImMessage> findLastMessageList(ImChatType chatType, List<ImChatId> chatIds);

    void save(ImPrivateChat imPrivateChat);

    void save(ImGroupChat imGroupChat);

    void saveGroupMembers(List<ImGroupMember> imGroupMembers);

    boolean containGroupMember(ImChatId chatId, UserId userId);

}
