package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：群内用户别名。
 */
public record GroupUserAlias(String value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public GroupUserAlias {
        AssertUtils.domainPropNotBlank("群内用户别名不能为空", value);
    }}
