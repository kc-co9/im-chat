package com.co.kc.imchat.service.message.domain.chat.repository;

import com.co.kc.imchat.service.message.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessage;
import com.co.kc.imchat.common.domain.user.model.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 资源库：群聊会话。
 */
public interface ImGroupChatRepository {

    Optional<ImGroupChat> find(ImChatId chatId);

    Optional<ImGroupChat> find(GroupId groupId, UserId userId);

    List<ImGroupChat> find(GroupId groupId);

    List<ImGroupChat> find(Collection<GroupId> groupIds);

    List<ImGroupChat> find(UserId userId);

    List<ImGroupChat> find(UserId userId, Collection<GroupId> groupIds);

    List<ImGroupChat> find(GroupId groupId, List<UserId> memberIds);

    List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer);

    void save(ImGroupChat groupChat);

    void save(List<ImGroupChat> groupChats);

    boolean contain(ImChatId chatId, UserId userId);

    void remove(GroupId groupId, UserId userId);
}
