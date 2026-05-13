package com.co.kc.imchat.domain.group;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@EqualsAndHashCode
public class ImGroupName {
    private final String value;

    public ImGroupName(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("群组名称不能为空");
        }
        this.value = value;
    }
}
