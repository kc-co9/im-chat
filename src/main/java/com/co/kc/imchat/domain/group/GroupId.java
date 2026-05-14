package com.co.kc.imchat.domain.group;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class GroupId {
    private final Long value;

    public GroupId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        this.value = value;
    }
}
