package com.co.kc.imchat.domain.chat.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：聊天会话展示名称。
 */
public record ImChatName(String value) {
    public ImChatName {
        AssertUtils.domainPropNotBlank("聊天名称不能为空", value);
    }}
