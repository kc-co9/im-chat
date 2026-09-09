package com.co.kc.imchat.management.audit.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 审计结果数据库枚举。 */
@Getter
@RequiredArgsConstructor
public enum DbAuditOutcome {
    SUCCESS(1),
    FAILURE(2);

    @EnumValue
    private final int value;
}
