package com.kim.omgchat.domain.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 聊天-消息令牌
 * <p>
 * 消息令牌为外部传入的消息ID，用作消息去重
 */
@Getter
@AllArgsConstructor
public class ImMessageToken {
    private final String value;
}
