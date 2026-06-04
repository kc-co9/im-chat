package com.co.kc.imchat.domain.group.model;

import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：群公告。
 */
public record GroupNotification(String value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public GroupNotification {
        value = StringUtils.defaultIfBlank(value, "");
    }
}
