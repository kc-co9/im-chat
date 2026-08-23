package com.co.kc.imchat.common.domain.group.model;

import com.co.kc.imchat.common.domain.shared.model.StringIdentifiable;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：群组ID。
 */
public record GroupId(Long value) implements Serializable, StringIdentifiable {
    @Serial
    private static final long serialVersionUID = 1L;

    public GroupId {
        AssertUtils.domainPropNotNull("群组ID不能为空", value);
    }

    @Override
    public String stringValue() {
        return value.toString();
    }
}
