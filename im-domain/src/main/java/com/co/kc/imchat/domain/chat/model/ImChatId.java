package com.co.kc.imchat.domain.chat.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：聊天会话ID。
 */
public record ImChatId(Long value) {
    public ImChatId {
        AssertUtils.domainPropNotNull("聊天ID不能为空", value);
    }}
