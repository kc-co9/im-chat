package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：群成员数量。
 */
public record MemberCount(Integer value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public MemberCount {
        AssertUtils.domainPropTrue("群人数不能小于0", value != null && value >= 0);
        AssertUtils.domainPropTrue("群人数不能超过 500 人", value <= GroupMembership.MAX_MEMBER_COUNT);
    }

    public MemberCount increase(Integer count) {
        AssertUtils.domainPropTrue("新增群人数不能小于0", count != null && count >= 0);
        return new MemberCount(value + count);
    }
}
