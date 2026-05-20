package com.co.kc.imchat.domain.group;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public class GroupNotification {
    private final String value;

    public GroupNotification(String value) {
        if (StringUtils.isBlank(value)) {
            this.value = "";
        } else {
            this.value = value;
        }
    }
}
