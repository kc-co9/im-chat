package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.group.GroupId;

public final class ImSystemMessageTokenFactory {
    private ImSystemMessageTokenFactory() {
    }

    public static ImMessageToken createSystemGroupCreated(GroupId groupId) {
        return ImSystemMessageType.GROUP_CREATED.token(groupId.getValue());
    }

    public static ImMessageToken createSystemGroupDismissed(GroupId groupId) {
        return ImSystemMessageType.GROUP_DISMISSED.token(groupId.getValue());
    }
}
