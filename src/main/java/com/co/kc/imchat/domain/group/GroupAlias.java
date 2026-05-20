package com.co.kc.imchat.domain.group;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@EqualsAndHashCode
public class GroupAlias {
    private final String value;

    public GroupAlias(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("群备注不能为空");
        }
        this.value = value;
    }
}
