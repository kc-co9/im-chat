package com.co.kc.imchat.domain.chat.model;

import com.co.kc.imchat.domain.group.model.GroupId;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * 值对象：群聊会话加入结果。
 */
public record GroupChatJoin(GroupId groupId, List<ImGroupChat> groupChats, List<ImGroupChat> newGroupChats) {
    public GroupChatJoin {
        groupChats = CollectionUtils.isEmpty(groupChats) ? Collections.emptyList() : groupChats;
        newGroupChats = CollectionUtils.isEmpty(newGroupChats) ? Collections.emptyList() : newGroupChats;
    }

    public GroupChatMembership describeChatMembership() {
        return new GroupChatMembership(groupId, groupChats);
    }
}
