package com.co.kc.imchat.management.iam.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** IAM 内部角色数据库状态。 */
@Getter
@RequiredArgsConstructor
public enum DbIamInternalRoleStatus {
    ACTIVE(1),
    DISABLED(2);

    @EnumValue
    private final int value;
}
