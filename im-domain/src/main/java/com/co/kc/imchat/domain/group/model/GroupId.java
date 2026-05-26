package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：群组ID。
 */
public record GroupId(Long value) {
    public GroupId {
        AssertUtils.domainPropNotNull("群组ID不能为空", value);
    }}
