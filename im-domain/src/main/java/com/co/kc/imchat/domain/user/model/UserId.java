package com.co.kc.imchat.domain.user.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：用户ID。
 */
public record UserId(Long value) {
    public UserId {
        AssertUtils.domainPropNotNull("用户ID不能为空", value);
    }}
