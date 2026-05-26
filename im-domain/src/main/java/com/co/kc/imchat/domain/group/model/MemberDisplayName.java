package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：群成员展示名称。
 */
public record MemberDisplayName(String value) {
    public MemberDisplayName {
        AssertUtils.domainPropNotBlank("群成员展示名称不能为空", value);
    }}
