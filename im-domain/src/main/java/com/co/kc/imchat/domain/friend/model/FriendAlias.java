package com.co.kc.imchat.domain.friend.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@EqualsAndHashCode
public class FriendAlias {
    private final String value;

    public FriendAlias(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("alias can not be blank");
        }
        this.value = value;
    }
}
