package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：群备注。
 */
public record GroupAlias(String value) {
    public GroupAlias {
        AssertUtils.domainPropNotBlank("群备注不能为空", value);
    }}
