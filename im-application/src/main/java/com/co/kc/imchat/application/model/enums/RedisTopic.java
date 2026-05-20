package com.co.kc.imchat.application.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RedisTopic {
    PRIVATE_MESSAGE_SEND("message:private:send"),
    PRIVATE_MESSAGE_REVOKE("message:private:revoke"),

    GROUP_MESSAGE_SEND("message:group:send"),
    GROUP_MESSAGE_REVOKE("message:group:revoke"),
    ;
    private final String value;
}
