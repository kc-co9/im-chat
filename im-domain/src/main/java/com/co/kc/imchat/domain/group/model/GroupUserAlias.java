package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：群内用户别名。
 */
public record GroupUserAlias(String value) {
    public GroupUserAlias {
        AssertUtils.domainPropNotBlank("群内用户别名不能为空", value);
    }}
