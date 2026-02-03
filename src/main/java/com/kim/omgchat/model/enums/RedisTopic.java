package com.kim.omgchat.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RedisTopic {
    PRIVATE_MESSAGE_SEND("message:private:send"),
    PRIVATE_MESSAGE_READ("message:private:read"),
    PRIVATE_MESSAGE_REVOKE("message:private:revoke"),

    GROUP_MESSAGE("message:group"),
    ;
    private final String value;
}
