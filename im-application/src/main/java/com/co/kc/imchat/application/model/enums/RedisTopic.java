package com.co.kc.imchat.application.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RedisTopic {
    PRIVATE_MESSAGE_SEND("im:message:private:sent"),
    PRIVATE_MESSAGE_REVOKE("im:message:private:revoked"),

    GROUP_MESSAGE_SEND("im:message:group:sent"),
    GROUP_MESSAGE_REVOKE("im:message:group:revoked"),
    ;
    private final String value;
}
