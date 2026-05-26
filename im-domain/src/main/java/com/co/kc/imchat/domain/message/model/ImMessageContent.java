package com.co.kc.imchat.domain.message.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：消息内容。
 */
public record ImMessageContent(ImMessageType type, String value) {
    public ImMessageContent {
        AssertUtils.domainPropNotNull("消息类型不能为空", type);
    }
}
