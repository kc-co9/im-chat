package com.co.kc.imchat.management.audit.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 审计类别数据库枚举。 */
@Getter
@RequiredArgsConstructor
public enum DbAuditType {
    BUSINESS(1),
    SECURITY(2);

    @EnumValue
    private final int value;
}
