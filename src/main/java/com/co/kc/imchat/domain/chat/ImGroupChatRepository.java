package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.user.UserId;

import java.util.Collection;
import java.util.List;

public interface ImGroupChatRepository {

    ImGroupChat find(ImChatId chatId);

    ImGroupChat find(GroupId groupId, UserId userId);

    List<ImGroupChat> find(GroupId groupId);

    List<ImGroupChat> find(Collection<GroupId> groupIds);

    List<ImGroupChat> find(UserId userId);

    List<ImGroupChat> find(UserId userId, Collection<GroupId> groupIds);

    List<ImGroupChat> findByUserIdAndChatIds(UserId userId, List<ImChatId> chatIds);

    List<ImGroupChat> findByUserIdsAndGroupId(GroupId groupId, List<UserId> userIds);

    List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer);

    void save(ImGroupChat groupChat);

    void saveAll(List<ImGroupChat> groupChats);

    boolean contain(ImChatId chatId, UserId userId);
}
