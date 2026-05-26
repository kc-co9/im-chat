package com.co.kc.imchat.application.support.lock;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DistributeLockScene {
    USER_SIGN_UP("im:user:sign:up"),
    FRIEND_ADD("im:friend:add"),
    PRIVATE_CHAT_OPEN("im:private:chat:open"),
    PRIVATE_MESSAGE_SEND("im:private:message:send"),
    PRIVATE_MESSAGE_REVOKE("im:private:message:revoke"),
    GROUP_CREATE("im:group:create"),
    GROUP_DISMISS("im:group:dismiss"),
    GROUP_MEMBER_INVITE("im:group:member:invite"),
    GROUP_OWNER_TRANSFER("im:group:owner:transfer"),
    GROUP_MEMBER_LEAVE("im:group:member:leave"),
    GROUP_MEMBER_KICK("im:group:member:kick"),
    GROUP_MESSAGE_SEND("im:group:message:send"),
    GROUP_MESSAGE_REVOKE("im:group:message:revoke");

    private final String value;
}
