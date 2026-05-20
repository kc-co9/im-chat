package com.co.kc.imchat.domain.group.model;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class MemberDisplayName {
    private String value;

    public MemberDisplayName(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("value cannot be null or empty");
        }
        this.value = value;
    }
}
