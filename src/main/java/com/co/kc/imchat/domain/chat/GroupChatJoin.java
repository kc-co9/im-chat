package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.group.GroupId;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

@Getter
public class GroupChatJoin {
    private final GroupId groupId;
    private final List<ImGroupChat> groupChats;
    private final List<ImGroupChat> newGroupChats;

    public GroupChatJoin(GroupId groupId, List<ImGroupChat> groupChats, List<ImGroupChat> newGroupChats) {
        this.groupId = groupId;
        this.groupChats = CollectionUtils.isEmpty(groupChats) ? Collections.emptyList() : groupChats;
        this.newGroupChats = CollectionUtils.isEmpty(newGroupChats) ? Collections.emptyList() : newGroupChats;
    }

    public GroupChatMembership describeChatMembership() {
        return new GroupChatMembership(this.getGroupId(), this.getGroupChats());
    }
}
