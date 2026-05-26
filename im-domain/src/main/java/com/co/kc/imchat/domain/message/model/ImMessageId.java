package com.co.kc.imchat.domain.message.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：消息ID。
 */
public record ImMessageId(Long value) {
    public ImMessageId {
        AssertUtils.domainPropNotNull("消息ID不能为空", value);
    }}
