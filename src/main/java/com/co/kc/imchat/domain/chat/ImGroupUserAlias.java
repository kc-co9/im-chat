package com.co.kc.imchat.domain.chat;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@EqualsAndHashCode
public class ImGroupUserAlias {
    private final String value;

    public ImGroupUserAlias(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("用户别名不能为空");
        }
        this.value = value;
    }
}
