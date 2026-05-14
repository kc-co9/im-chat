package com.co.kc.imchat.domain.group;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@EqualsAndHashCode
public class GroupUserAlias {
    private final String value;

    public GroupUserAlias(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("用户别名不能为空");
        }
        this.value = value;
    }
}
