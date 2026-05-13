package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.user.UserId;

import java.util.List;

public interface ImGroupChatRepository {

    ImGroupChat find(ImChatId chatId);

    ImGroupChat find(ImGroupId groupId, UserId userId);

    List<ImGroupChat> findByGroupId(ImGroupId groupId);

    List<ImGroupChat> findByUserId(UserId userId);

    List<ImGroupChat> findByUserIdAndChatIds(UserId userId, List<ImChatId> chatIds);

    List<ImGroupChat> findByUserIdsAndGroupId(ImGroupId groupId, List<UserId> userIds);

    List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer);

    void save(ImGroupChat groupChat);

    void saveAll(List<ImGroupChat> groupChats);

    boolean contain(ImChatId chatId, UserId userId);
}
