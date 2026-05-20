package com.co.kc.imchat.domain.message.model;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * 聊天-消息令牌
 * <p>
 * 消息令牌为外部传入的消息ID，用作消息去重
 */
@Getter
public class ImMessageToken {
    private final String value;

    public ImMessageToken(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("消息令牌不能为空");
        }
        this.value = value;
    }
}
