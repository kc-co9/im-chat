package com.co.kc.imchat.common.domain.chat.model;

import com.co.kc.imchat.common.domain.shared.model.StringIdentifiable;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：聊天会话 ID。
 */
public record ImChatId(Long value) implements Serializable, StringIdentifiable {
    @Serial
    private static final long serialVersionUID = 1L;

    public ImChatId {
        AssertUtils.domainPropNotNull("聊天会话ID不能为空", value);
    }

    @Override
    public String stringValue() {
        return value.toString();
    }
}
