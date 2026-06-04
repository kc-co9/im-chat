package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：群备注。
 */
public record GroupAlias(String value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public GroupAlias {
        AssertUtils.domainPropNotBlank("群备注不能为空", value);
    }}
