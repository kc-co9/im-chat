package com.co.kc.imchat.service.message.domain.message.model;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
public enum ImSystemMessageType {
    GROUP_CREATED("system:group_created:", "群聊已创建"),
    GROUP_DISMISSED("system:group_dismissed:", "群聊已解散"),
    GROUP_MEMBER_JOINED("system:group_member_joined:", "成员加入群聊");

    private final String tokenPrefix;
    private final String defaultContent;

    public ImMessageToken token(Long identity) {
        return new ImMessageToken(tokenPrefix + identity);
    }

    public ImMessageToken token(String identity) {
        return new ImMessageToken(tokenPrefix + identity);
    }

    public String content() {
        return defaultContent;
    }

    public String joinedContent(String joinedNames) {
        if (StringUtils.isBlank(joinedNames)) {
            return defaultContent;
        }
        return joinedNames + " 加入群聊";
    }
}
