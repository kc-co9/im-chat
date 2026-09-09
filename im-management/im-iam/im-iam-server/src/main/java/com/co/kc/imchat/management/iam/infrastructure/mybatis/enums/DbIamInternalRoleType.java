package com.co.kc.imchat.management.iam.infrastructure.mybatis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** IAM 内部角色数据库类型。 */
@Getter
@RequiredArgsConstructor
public enum DbIamInternalRoleType {
    SUPER_ADMIN(1),
    CUSTOM(2);

    @EnumValue
    private final int value;
}
