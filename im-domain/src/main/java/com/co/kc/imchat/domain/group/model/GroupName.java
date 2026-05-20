package com.co.kc.imchat.domain.group.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@EqualsAndHashCode
public class GroupName {
    private final String value;

    public GroupName(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("群组名称不能为空");
        }
        this.value = value;
    }
}
