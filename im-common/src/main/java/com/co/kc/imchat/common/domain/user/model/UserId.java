package com.co.kc.imchat.common.domain.user.model;

import com.co.kc.imchat.common.domain.shared.model.StringIdentifiable;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：用户ID。
 */
public record UserId(Long value) implements Serializable, StringIdentifiable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserId {
        AssertUtils.domainPropNotNull("用户ID不能为空", value);
    }

    @Override
    public String stringValue() {
        return value.toString();
    }
}
