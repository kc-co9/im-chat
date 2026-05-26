package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：群名称。
 */
public record GroupName(String value) {
    public GroupName {
        AssertUtils.domainPropNotBlank("群组名称不能为空", value);
    }}
