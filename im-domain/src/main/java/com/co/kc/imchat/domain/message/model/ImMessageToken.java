package com.co.kc.imchat.domain.message.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：消息令牌，用作外部传入消息的幂等标识。
 */
public record ImMessageToken(String value) {
    public ImMessageToken {
        AssertUtils.domainPropNotBlank("消息令牌不能为空", value);
    }}
