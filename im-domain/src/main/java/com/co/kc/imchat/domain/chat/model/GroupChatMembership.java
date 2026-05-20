package com.co.kc.imchat.domain.chat.model;

import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.user.model.UserId;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
public class GroupChatMembership {
    private final GroupId groupId;
    private final List<ImGroupChat> chats;

    public GroupChatMembership(GroupId groupId, List<ImGroupChat> chats) {
        if (groupId == null) {
            throw new IllegalArgumentException("群组 ID 不能为空");
        }
        this.groupId = groupId;
        this.chats = CollectionUtils.isEmpty(chats) ? Collections.emptyList() : chats;
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
