package com.co.kc.imchat.domain.group.model;

import org.apache.commons.lang3.StringUtils;

/**
 * 值对象：群公告。
 */
public record GroupNotification(String value) {
    public GroupNotification {
        value = StringUtils.defaultIfBlank(value, "");
    }
}
