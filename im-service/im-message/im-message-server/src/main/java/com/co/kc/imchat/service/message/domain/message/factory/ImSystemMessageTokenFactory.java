package com.co.kc.imchat.service.message.domain.message.factory;

import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImSystemMessageType;

public final class ImSystemMessageTokenFactory {
    private ImSystemMessageTokenFactory() {
    }

    public static ImMessageToken createSystemGroupCreated(GroupId groupId) {
        return ImSystemMessageType.GROUP_CREATED.token(groupId.value());
    }

    public static ImMessageToken createSystemGroupDismissed(GroupId groupId) {
        return ImSystemMessageType.GROUP_DISMISSED.token(groupId.value());
    }

    public static ImMessageToken createSystemGroupMemberJoined(GroupId groupId, ImMessageId messageId) {
        return ImSystemMessageType.GROUP_MEMBER_JOINED.token(groupId.value() + ":" + messageId.value());
    }
}
