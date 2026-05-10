package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface ImGroupChatRepository {

    List<ImGroupChat> findGroupChatList(UserId userId);

    List<ImGroupMember> findGroupMemberList(ImChatId chatId);

    List<ImGroupMember> findUserGroupMemberList(UserId userId, List<ImChatId> chatIds);

    ImGroupChat findGroupChat(ImChatId chatId);

    List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer);

    void save(ImGroupChat imGroupChat);

    void saveGroupMembers(List<ImGroupMember> imGroupMembers);

    boolean containGroupMember(ImChatId chatId, UserId userId);
}
