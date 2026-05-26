package com.co.kc.imchat.domain.chat.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.user.model.UserId;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 值对象：群聊会话成员快照。
 */
public record GroupChatMembership(GroupId groupId, List<ImGroupChat> chats) {
    public GroupChatMembership {
        AssertUtils.domainPropNotNull("群组 ID 不能为空", groupId);
        chats = CollectionUtils.isEmpty(chats) ? Collections.emptyList() : chats;
    }

    public Optional<ImGroupChat> findOwnerChat(UserId ownerId) {
        return findMemberChat(ownerId);
    }

    public Optional<ImGroupChat> findMemberChat(UserId userId) {
        return chats.stream()
                .filter(chat -> chat.belongsTo(userId))
                .findFirst();
    }
}
