package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.group.ImGroupId;

public final class ImSystemMessageTokenFactory {
    private ImSystemMessageTokenFactory() {
    }

    public static ImMessageToken createSystemGroupCreated(ImGroupId groupId) {
        return ImSystemMessageType.GROUP_CREATED.token(groupId.getValue());
    }
}
