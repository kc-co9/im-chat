package com.co.kc.imchat.domain.group.model;

import lombok.Value;

@Value
public class MemberCount {
    Integer value;

    public MemberCount(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("群人数不能小于0");
        }
        if (value > GroupMembership.MAX_MEMBER_COUNT) {
            throw new IllegalArgumentException("群人数不能超过 500 人");
        }
        this.value = value;
    }

    public MemberCount increase(Integer count) {
        if (count == null || count < 0) {
            throw new IllegalArgumentException("新增群人数不能小于0");
        }
        return new MemberCount(value + count);
    }
}
