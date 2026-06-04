package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：群名称。
 */
public record GroupName(String value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public GroupName {
        AssertUtils.domainPropNotBlank("群组名称不能为空", value);
    }}
