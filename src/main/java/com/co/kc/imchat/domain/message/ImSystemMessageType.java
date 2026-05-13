package com.co.kc.imchat.domain.message;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ImSystemMessageType {
    GROUP_CREATED("system:group_created:");

    private final String tokenPrefix;

    public ImMessageToken token(Long identity) {
        return new ImMessageToken(tokenPrefix + identity);
    }
}
